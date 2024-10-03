/*
 * Copyright 2017-2023 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.sourcegen.generator.visitors;

import io.micronaut.core.annotation.*;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.processing.ProcessingException;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.sourcegen.annotations.EqualsAndHashCode;
import io.micronaut.sourcegen.annotations.ToString;
import io.micronaut.sourcegen.generator.SourceGenerator;
import io.micronaut.sourcegen.generator.SourceGenerators;
import io.micronaut.sourcegen.model.*;

import javax.lang.model.element.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The visitor that generates the Object class of a bean.
 * The Object class can have functions substituting toString, equals, and hashcode.
 * However, each method needs to be annotated to be generated.
 *      {@link ToString} annotation for toString function
 *      {@link EqualsAndHashCode} annotation for equals and hashCode functions
 *
 * @author Elif Kurtay
 * @since 1.3
 */

@Internal
public final class ObjectAnnotationVisitor implements TypeElementVisitor<Object, Object> {

    private static final int NULL_HASH_VALUE = 43;
    private static final int TRUE_HASH_VALUE = 79;
    private static final int FALSE_HASH_VALUE = 97;
    private static final int HASH_MULTIPLIER = 59;
    private final Set<String> processed = new HashSet<>();

    @Override
    public @NonNull VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }

    @Override
    public void start(VisitorContext visitorContext) {
        processed.clear();
    }

    @Override
    public Set<String> getSupportedAnnotationNames() {
        return Set.of(ToString.class.getName(), EqualsAndHashCode.class.getName());
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        if (!(element.hasStereotype(ToString.class) || element.hasStereotype(EqualsAndHashCode.class))) {
            return;
        }

        if (processed.contains(element.getName())) {
            return;
        }
        try {
            String simpleName = element.getSimpleName() + "Object";
            String objectClassName = element.getPackageName() + "." + simpleName;

            // class def and annotations
            ClassDef.ClassDefBuilder objectBuilder = ClassDef.builder(objectClassName)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL);

            // create the utils functions if they are annotated
            if (element.hasStereotype(ToString.class)) {
                context.warn("@ToString annotation will only print out bean properties.", element);
                List<PropertyElement> filteredProperties = element.getBeanProperties().stream()
                    .filter(property -> !property.hasAnnotation(ToString.Exclude.class)).toList();
                createToStringMethod(objectBuilder, ClassTypeDef.of(element), filteredProperties);
            }
            if (element.hasStereotype(EqualsAndHashCode.class)) {
                List<PropertyElement> properties = element.getBeanProperties();
                createEqualsMethod(objectBuilder, ClassTypeDef.of(element), properties);
                createHashCodeMethod(objectBuilder, ClassTypeDef.of(element), properties);
            }

            SourceGenerator sourceGenerator = SourceGenerators.findByLanguage(context.getLanguage()).orElse(null);
            if (sourceGenerator == null) {
                return;
            }

            ClassDef objectDef = objectBuilder.build();
            processed.add(element.getName());
            context.visitGeneratedSourceFile(
                objectDef.getPackageName(),
                objectDef.getSimpleName(),
                element
            ).ifPresent(sourceFile -> {
                try {
                    sourceFile.write(
                        writer -> sourceGenerator.write(objectDef, writer)
                    );
                } catch (Exception e) {
                    throw new ProcessingException(element, "Failed to generate a ObjectBuilder: " + e.getMessage(), e);
                }
            });
        } catch (ProcessingException e) {
            throw e;
        } catch (Exception e) {
            SourceGenerators.handleFatalException(
                element,
                ToString.class,
                e,
                (exception -> {
                    processed.remove(element.getName());
                    throw exception;
                })
            );
        }
    }

    /*
    Creates a toString method with signature:
        public static String BeanNameObject.toString(BeanName object)
     */
    private static void createToStringMethod(ClassDef.ClassDefBuilder classDefBuilder, ClassTypeDef selfType, List<PropertyElement> properties) {
        MethodDef method = MethodDef.builder("toString")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .returns(TypeDef.STRING)
            .addParameter("instance", selfType)
            .build((self, parameterDef) ->
                ClassTypeDef.of(StringBuilder.class).instantiate(
                    ExpressionDef.constant(selfType.getSimpleName() + "["))
                    .newLocal("strBuilder", variableDef -> {
                    ExpressionDef exp = variableDef;
                    for (int i = 0; i < properties.size(); i++) {
                        var beanProperty = properties.get(i);
                        Optional<MethodElement> readMethod = beanProperty.getReadMethod();
                        if (readMethod.isEmpty()) {
                            continue;
                        }
                        ExpressionDef propertyValue = parameterDef.get(0).invoke(readMethod.get());

                        exp = exp.invoke("append", variableDef.type(),
                                ExpressionDef.constant(beanProperty.getName() + "="))
                            .invoke("append", variableDef.type(),
                                TypeDef.of(beanProperty.getType()).isArray() ?
                                    ClassTypeDef.of(Arrays.class).invokeStatic("toString", TypeDef.STRING, propertyValue)
                                    : propertyValue
                            ).invoke("append", variableDef.type(),
                                ExpressionDef.constant((i == properties.size() - 1) ? "]" : ", "));
                    }
                    return exp.invoke("toString", TypeDef.STRING).returning();
                })
            );
        classDefBuilder.addMethod(method);
    }

    /*
    Creates an equals method with signature:
        public static boolean BeanNameObject.equals(BeanName object1, Object object2)
     */
    private static void createEqualsMethod(ClassDef.ClassDefBuilder classDefBuilder, ClassTypeDef selfType, List<PropertyElement> properties) {
        MethodDef method = MethodDef.builder("equals")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .returns(TypeDef.Primitive.BOOLEAN)
            .addParameter("instance", selfType)
            .addParameter("o", TypeDef.OBJECT)
            .build((self, parameterDef) -> {
                VariableDef instance = parameterDef.get(0);
                VariableDef o = parameterDef.get(1);

                return StatementDef.multi(
                    instance.asCondition(" == ", o)
                        .asConditionIf(ExpressionDef.trueValue().returning()),
                    o.isNull().asConditionOr(
                        instance.invoke("getClass", ClassTypeDef.of("Class"))
                            .asCondition(" != ", o.invoke("getClass", ClassTypeDef.of("Class"))))
                        .asConditionIf(ExpressionDef.falseValue().returning()),
                    o.cast(selfType).newLocal("other", variableDef -> {
                        ExpressionDef exp = null;
                        for (PropertyElement beanProperty : properties) {
                            if (beanProperty.hasAnnotation(EqualsAndHashCode.Exclude.class)) {
                                continue;
                            }
                            Optional<MethodElement> readMethod = beanProperty.getReadMethod();
                            if (readMethod.isEmpty()) {
                                continue;
                            }
                            var firstProperty = instance.invoke(readMethod.get());
                            var secondProperty = variableDef.invoke(readMethod.get());

                            ExpressionDef newEqualsExpression = firstProperty.asCondition(" == ", secondProperty);
                            if (!beanProperty.isPrimitive() || beanProperty.isArray()) {
                                // Object.equals for objects
                                ExpressionDef equalsMethod = firstProperty.invoke("equals", TypeDef.Primitive.BOOLEAN, secondProperty);
                                if (beanProperty.isArray()) {
                                    // Arrays.equals or Arrays.deepEquals for Array
                                    String methodName = beanProperty.getArrayDimensions() > 1 ?  "deepEquals" : "equals";
                                    equalsMethod = ClassTypeDef.of(Arrays.class).invokeStatic(methodName, TypeDef.Primitive.BOOLEAN, firstProperty, secondProperty);
                                }
                                newEqualsExpression = newEqualsExpression
                                    .asConditionOr(firstProperty.isNonNull().asConditionAnd(equalsMethod));
                            }

                            if (exp == null) {
                                exp = newEqualsExpression;
                            } else {
                                exp = exp.asConditionAnd(newEqualsExpression);
                            }
                        }
                        return Objects.requireNonNullElseGet(exp, ExpressionDef::trueValue).returning();
                    })
                );
            });
        classDefBuilder.addMethod(method);
    }

    /*
    Creates a hashCode method with signature:
        public static int BeanNameObject.hashCode(BeanName object)
     */
    private static void createHashCodeMethod(ClassDef.ClassDefBuilder classDefBuilder, ClassTypeDef selfType, List<PropertyElement> properties) {
        MethodDef method = MethodDef.builder("hashCode")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addParameter("instance", selfType)
            .returns(TypeDef.Primitive.INT)
            .build((self, parameterDef) -> StatementDef.multi(
                    parameterDef.get(0).isNull().asConditionIf(ExpressionDef.constant(0).returning()),
                    TypeDef.Primitive.INT.initialize(1).newLocal("hashValue", hashValue -> {
                        List<StatementDef> hashUpdates = new ArrayList<>();
                        properties.stream().filter(beanProperty -> !beanProperty.hasAnnotation(EqualsAndHashCode.Exclude.class) && beanProperty.getReadMethod().isPresent())
                            .forEach(property -> {
                                ExpressionDef propertyGetter = parameterDef.get(0)
                                    .invoke(property.getReadMethod().get());
                                hashUpdates.add(hashValue.assign(
                                    hashValue.asCondition(" * ", ExpressionDef.constant(HASH_MULTIPLIER)
                                        .asCondition(" + ", getPropertyHashValue(TypeDef.of(property.getType()), propertyGetter)))));
                            });
                        hashUpdates.add(hashValue.returning());
                        return StatementDef.multi(hashUpdates);
                    })
                )
            );
        classDefBuilder.addMethod(method);
    }

    /** Calculate property hash value according to its type.
     *
     * @param propertyTypeDef TypeDef of the property
     * @param propertyGetter the expression that gets the value of the property
     * @return expression that calculates the hash value
     */
    private static ExpressionDef getPropertyHashValue(TypeDef propertyTypeDef, ExpressionDef propertyGetter) {
        ExpressionDef propertyHashCalculation;
        if (propertyTypeDef.isArray()) {
            String methodName = (((TypeDef.Array) propertyTypeDef).dimensions() > 1) ?  "deepHashCode" : "hashCode";
            propertyHashCalculation = ClassTypeDef.of(Arrays.class).invokeStatic(methodName, TypeDef.Primitive.INT, propertyGetter);
        } else if (propertyTypeDef.isPrimitive()) {
            if (propertyTypeDef.equals(TypeDef.Primitive.BOOLEAN)) {
                propertyHashCalculation = propertyGetter.asConditionIfElse(
                    ExpressionDef.constant(TRUE_HASH_VALUE),
                    ExpressionDef.constant(FALSE_HASH_VALUE));
            } else if (propertyTypeDef.equals(TypeDef.Primitive.FLOAT)) {
                propertyHashCalculation = ClassTypeDef.of(Float.class).invokeStatic("floatToIntBits", TypeDef.Primitive.INT, propertyGetter);
            } else if (propertyTypeDef.equals(TypeDef.Primitive.DOUBLE)) {
                // double -> long -> int
                propertyHashCalculation = ClassTypeDef.of(Double.class).invokeStatic("doubleToLongBits", TypeDef.Primitive.INT, propertyGetter);
                propertyHashCalculation = propertyHashCalculation.asCondition(" >>> ",
                    ExpressionDef.constant(32).asCondition(" ^ ", propertyHashCalculation));
            } else if (propertyTypeDef.equals(TypeDef.Primitive.LONG)) {
                propertyHashCalculation = propertyGetter.asCondition(" >>> ",
                    ExpressionDef.constant(32).asCondition(" ^ ", propertyGetter));
            } else if (propertyTypeDef.equals(TypeDef.Primitive.CHAR)) {
                propertyHashCalculation = propertyGetter.asCondition(" - ", ExpressionDef.constant('0'));
            } else if (propertyTypeDef.equals(TypeDef.Primitive.SHORT)) {
                propertyHashCalculation = propertyGetter.asCondition(" & ", ExpressionDef.constant(0xffff));
            } else { // for int and byte, return itself as an int
                propertyHashCalculation = propertyGetter;
            }
        } else { // OBJECT
            propertyHashCalculation = propertyGetter.isNull().asConditionIfElse(
                ExpressionDef.constant(NULL_HASH_VALUE),
                propertyGetter.invoke("hashCode", TypeDef.Primitive.INT)
            );
        }
        return propertyHashCalculation.cast(TypeDef.Primitive.INT);
    }
}