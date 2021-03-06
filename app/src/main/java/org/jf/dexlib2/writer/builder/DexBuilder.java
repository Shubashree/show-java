/*
 * Copyright 2013, Google Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *     * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer
 * in the documentation and/or other materials provided with the
 * distribution.
 *     * Neither the name of Google Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.jf.dexlib2.writer.builder;

import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.jf.dexlib2.ValueType;
import org.jf.dexlib2.iface.Annotation;
import org.jf.dexlib2.iface.MethodImplementation;
import org.jf.dexlib2.iface.MethodParameter;
import org.jf.dexlib2.iface.reference.FieldReference;
import org.jf.dexlib2.iface.reference.MethodReference;
import org.jf.dexlib2.iface.reference.Reference;
import org.jf.dexlib2.iface.reference.StringReference;
import org.jf.dexlib2.iface.reference.TypeReference;
import org.jf.dexlib2.iface.value.BooleanEncodedValue;
import org.jf.dexlib2.iface.value.ByteEncodedValue;
import org.jf.dexlib2.iface.value.CharEncodedValue;
import org.jf.dexlib2.iface.value.DoubleEncodedValue;
import org.jf.dexlib2.iface.value.EncodedValue;
import org.jf.dexlib2.iface.value.FloatEncodedValue;
import org.jf.dexlib2.iface.value.IntEncodedValue;
import org.jf.dexlib2.iface.value.LongEncodedValue;
import org.jf.dexlib2.iface.value.ShortEncodedValue;
import org.jf.dexlib2.writer.DexWriter;
import org.jf.dexlib2.writer.builder.BuilderEncodedValues.BuilderAnnotationEncodedValue;
import org.jf.dexlib2.writer.builder.BuilderEncodedValues.BuilderArrayEncodedValue;
import org.jf.dexlib2.writer.builder.BuilderEncodedValues.BuilderEncodedValue;
import org.jf.dexlib2.writer.builder.BuilderEncodedValues.BuilderEnumEncodedValue;
import org.jf.dexlib2.writer.builder.BuilderEncodedValues.BuilderFieldEncodedValue;
import org.jf.dexlib2.writer.builder.BuilderEncodedValues.BuilderMethodEncodedValue;
import org.jf.dexlib2.writer.builder.BuilderEncodedValues.BuilderStringEncodedValue;
import org.jf.dexlib2.writer.builder.BuilderEncodedValues.BuilderTypeEncodedValue;
import org.jf.util.ExceptionWithContext;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;

public class DexBuilder extends DexWriter<BuilderStringReference, BuilderStringReference, BuilderTypeReference,
        BuilderTypeReference, BuilderProtoReference, BuilderFieldReference, BuilderMethodReference,
        BuilderClassDef, BuilderAnnotation, BuilderAnnotationSet, BuilderTypeList, BuilderField, BuilderMethod,
        BuilderEncodedValue, BuilderAnnotationElement> {

    private final BuilderContext context;

    public static DexBuilder makeDexBuilder() {
        BuilderContext context = new BuilderContext();
        return new DexBuilder(15, context);
    }

    public static DexBuilder makeDexBuilder(int api) {
        BuilderContext context = new BuilderContext();
        return new DexBuilder(api, context);
    }

    private DexBuilder(int api, @Nonnull BuilderContext context) {
        super(api, context.stringPool, context.typePool, context.protoPool,
                context.fieldPool, context.methodPool, context.classPool, context.typeListPool, context.annotationPool,
                context.annotationSetPool);
        this.context = context;
    }

    @Nonnull public BuilderField internField(@Nonnull String definingClass,
                                             @Nonnull String name,
                                             @Nonnull String type,
                                             int accessFlags,
                                             @Nullable EncodedValue initialValue,
                                             @Nonnull Set<? extends Annotation> annotations) {
        return new BuilderField(context.fieldPool.internField(definingClass, name, type),
                accessFlags,
                context.internNullableEncodedValue(initialValue),
                context.annotationSetPool.internAnnotationSet(annotations));
    }

    @Nonnull public BuilderMethod internMethod(@Nonnull String definingClass,
                                               @Nonnull String name,
                                               @Nullable List<? extends MethodParameter> parameters,
                                               @Nonnull String returnType,
                                               int accessFlags,
                                               @Nonnull Set<? extends Annotation> annotations,
                                               @Nullable MethodImplementation methodImplementation) {
        if (parameters == null) {
            parameters = ImmutableList.of();
        }
        return new BuilderMethod(context.methodPool.internMethod(definingClass, name, parameters, returnType),
                internMethodParameters(parameters),
                accessFlags,
                context.annotationSetPool.internAnnotationSet(annotations),
                methodImplementation);
    }

    @Nonnull public BuilderClassDef internClassDef(@Nonnull String type,
                                                   int accessFlags,
                                                   @Nullable String superclass,
                                                   @Nullable List<String> interfaces,
                                                   @Nullable String sourceFile,
                                                   @Nonnull Set<? extends Annotation> annotations,
                                                   @Nullable Iterable<? extends BuilderField> fields,
                                                   @Nullable Iterable<? extends BuilderMethod> methods) {
        if (interfaces == null) {
            interfaces = ImmutableList.of();
        } else {
            interfaces = Lists.newArrayList(interfaces);
            Collections.sort(interfaces);
            String prev = null;
            Iterator<String> interfaceIterator = interfaces.iterator();
            while (interfaceIterator.hasNext()) {
                String iface = interfaceIterator.next();
                if (prev != null && iface.equals(prev)) {
                    interfaceIterator.remove();
                }
                prev = iface;
            }
        }

        return context.classPool.internClass(new BuilderClassDef(context.typePool.internType(type),
                accessFlags,
                context.typePool.internNullableType(superclass),
                context.typeListPool.internTypeList(interfaces),
                context.stringPool.internNullableString(sourceFile),
                context.annotationSetPool.internAnnotationSet(annotations),
                fields,
                methods));
    }

    @Nonnull public BuilderStringReference internStringReference(@Nonnull String string) {
        return context.stringPool.internString(string);
    }

    @Nullable public BuilderStringReference internNullableStringReference(@Nullable String string) {
        if (string != null) {
            return internStringReference(string);
        }
        return null;
    }

    @Nonnull public BuilderTypeReference internTypeReference(@Nonnull String type) {
        return context.typePool.internType(type);
    }

    @Nullable public BuilderTypeReference internNullableTypeReference(@Nullable String type) {
        if (type != null) {
            return internTypeReference(type);
        }
        return null;
    }

    @Nonnull public BuilderFieldReference internFieldReference(@Nonnull FieldReference field) {
        return context.fieldPool.internField(field);
    }

    @Nonnull public BuilderMethodReference internMethodReference(@Nonnull MethodReference method) {
        return context.methodPool.internMethod(method);
    }

    @Nonnull public BuilderReference internReference(@Nonnull Reference reference) {
        if (reference instanceof StringReference) {
            return internStringReference(((StringReference)reference).getString());
        }
        if (reference instanceof TypeReference) {
            return internTypeReference(((TypeReference)reference).getType());
        }
        if (reference instanceof MethodReference) {
            return internMethodReference((MethodReference)reference);
        }
        if (reference instanceof FieldReference) {
            return internFieldReference((FieldReference)reference);
        }
        throw new IllegalArgumentException("Could not determine type of reference");
    }

    @Nonnull private List<BuilderMethodParameter> internMethodParameters(
            @Nullable List<? extends MethodParameter> methodParameters) {
        if (methodParameters == null) {
            return ImmutableList.of();
        }
        return ImmutableList.copyOf(Iterators.transform(methodParameters.iterator(),
                new Function<MethodParameter, BuilderMethodParameter>() {
                    @Nullable @Override public BuilderMethodParameter apply(MethodParameter input) {
                        return internMethodParameter(input);
                    }
                }));
    }

    @Nonnull private BuilderMethodParameter internMethodParameter(@Nonnull MethodParameter methodParameter) {
        return new BuilderMethodParameter(
                context.typePool.internType(methodParameter.getType()),
                context.stringPool.internNullableString(methodParameter.getName()),
                context.annotationSetPool.internAnnotationSet(methodParameter.getAnnotations()));
    }

    @Override protected void writeEncodedValue(@Nonnull InternalEncodedValueWriter writer,
                                               @Nonnull BuilderEncodedValue encodedValue) throws IOException {
        switch (encodedValue.getValueType()) {
            case ValueType.ANNOTATION:
                BuilderAnnotationEncodedValue annotationEncodedValue = (BuilderAnnotationEncodedValue)encodedValue;
                writer.writeAnnotation(annotationEncodedValue.typeReference, annotationEncodedValue.elements);
                break;
            case ValueType.ARRAY:
                BuilderArrayEncodedValue arrayEncodedValue = (BuilderArrayEncodedValue)encodedValue;
                writer.writeArray(arrayEncodedValue.elements);
                break;
            case ValueType.BOOLEAN:
                writer.writeBoolean(((BooleanEncodedValue)encodedValue).getValue());
                break;
            case ValueType.BYTE:
                writer.writeByte(((ByteEncodedValue)encodedValue).getValue());
                break;
            case ValueType.CHAR:
                writer.writeChar(((CharEncodedValue)encodedValue).getValue());
                break;
            case ValueType.DOUBLE:
                writer.writeDouble(((DoubleEncodedValue)encodedValue).getValue());
                break;
            case ValueType.ENUM:
                writer.writeEnum(((BuilderEnumEncodedValue)encodedValue).getValue());
                break;
            case ValueType.FIELD:
                writer.writeField(((BuilderFieldEncodedValue)encodedValue).fieldReference);
                break;
            case ValueType.FLOAT:
                writer.writeFloat(((FloatEncodedValue)encodedValue).getValue());
                break;
            case ValueType.INT:
                writer.writeInt(((IntEncodedValue)encodedValue).getValue());
                break;
            case ValueType.LONG:
                writer.writeLong(((LongEncodedValue)encodedValue).getValue());
                break;
            case ValueType.METHOD:
                writer.writeMethod(((BuilderMethodEncodedValue)encodedValue).methodReference);
                break;
            case ValueType.NULL:
                writer.writeNull();
                break;
            case ValueType.SHORT:
                writer.writeShort(((ShortEncodedValue)encodedValue).getValue());
                break;
            case ValueType.STRING:
                writer.writeString(((BuilderStringEncodedValue)encodedValue).stringReference);
                break;
            case ValueType.TYPE:
                writer.writeType(((BuilderTypeEncodedValue)encodedValue).typeReference);
                break;
            default:
                throw new ExceptionWithContext("Unrecognized value type: %d", encodedValue.getValueType());
        }
    }
}
