/*
 * Copyright 2021 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.utils.protobuf.schema;


import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.TimestampProto;
import com.google.protobuf.WrappersProto;
import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumDescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumValueDescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.OneofDescriptorProto;
import com.google.protobuf.Descriptors.DescriptorValidationException;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.squareup.wire.Syntax;
import com.squareup.wire.schema.Field;
import com.squareup.wire.schema.Location;
import com.squareup.wire.schema.internal.parser.EnumConstantElement;
import com.squareup.wire.schema.internal.parser.EnumElement;
import com.squareup.wire.schema.internal.parser.FieldElement;
import com.squareup.wire.schema.internal.parser.MessageElement;
import com.squareup.wire.schema.internal.parser.OneOfElement;
import com.squareup.wire.schema.internal.parser.OptionElement;
import com.squareup.wire.schema.internal.parser.ProtoFileElement;
import com.squareup.wire.schema.internal.parser.ReservedElement;
import com.squareup.wire.schema.internal.parser.TypeElement;

/**
 * @author Fabian Martinez
 */
public class FileDescriptorUtils {

    public static final Location DEFAULT_LOCATION = Location.get("");

    private static final String PROTO2 = "proto2";
    private static final String PROTO3 = "proto3";
    private static final String ALLOW_ALIAS_OPTION = "allow_alias";
    private static final String MAP_ENTRY_OPTION = "map_entry";
    private static final String PACKED_OPTION = "packed";
    private static final String JSON_NAME_OPTION = "json_name";
    private static final String DEPRECATED_OPTION = "deprecated";
    private static final String JAVA_MULTIPLE_FILES_OPTION = "java_multiple_files";
    private static final String JAVA_OUTER_CLASSNAME_OPTION = "java_outer_classname";
    private static final String JAVA_PACKAGE_OPTION = "java_package";


    public static FileDescriptor[] baseDependencies() {
        return new FileDescriptor[] {
                    TimestampProto.getDescriptor().getFile(),
                    WrappersProto.getDescriptor().getFile()
                };
    }

    public static FileDescriptor protoFileToFileDescriptor(ProtoFileElement element) throws DescriptorValidationException {
        return FileDescriptor.buildFrom(SchemaToProto.toFileDescriptorProto(element), baseDependencies());
    }


    public static ProtoFileElement fileDescriptorToProtoFile(FileDescriptorProto file) {
        String packageName = file.getPackage();
        if ("".equals(packageName)) {
            packageName = null;
        }

        Syntax syntax = null;
        switch (file.getSyntax()) {
            case PROTO2:
                syntax = Syntax.PROTO_2;
                break;
            case PROTO3:
                syntax = Syntax.PROTO_3;
                break;
            default:
                break;
        }
        ImmutableList.Builder<TypeElement> types = ImmutableList.builder();
        for (DescriptorProto md : file.getMessageTypeList()) {
            MessageElement message = toMessage(file, md);
            types.add(message);
        }
        for (EnumDescriptorProto ed : file.getEnumTypeList()) {
            EnumElement enumer = toEnum(ed);
            types.add(enumer);
        }
        ImmutableList.Builder<String> imports = ImmutableList.builder();
        ImmutableList.Builder<String> publicImports = ImmutableList.builder();
        List<String> dependencyList = file.getDependencyList();
        Set<Integer> publicDependencyList = new HashSet<>(file.getPublicDependencyList());
        for (int i = 0; i < dependencyList.size(); i++) {
            String depName = dependencyList.get(i);
            if (publicDependencyList.contains(i)) {
                publicImports.add(depName);
            } else {
                imports.add(depName);
            }
        }
        ImmutableList.Builder<OptionElement> options = ImmutableList.builder();
        if (file.getOptions().hasJavaPackage()) {
            OptionElement.Kind kind = OptionElement.Kind.STRING;
            OptionElement option = new OptionElement(JAVA_PACKAGE_OPTION, kind, file.getOptions().getJavaPackage(), false);
            options.add(option);
        }
        if (file.getOptions().hasJavaOuterClassname()) {
            OptionElement.Kind kind = OptionElement.Kind.STRING;
            OptionElement option = new OptionElement(JAVA_OUTER_CLASSNAME_OPTION, kind, file.getOptions().getJavaOuterClassname(), false);
            options.add(option);
        }
        if (file.getOptions().hasJavaMultipleFiles()) {
            OptionElement.Kind kind = OptionElement.Kind.BOOLEAN;
            OptionElement option = new OptionElement(JAVA_MULTIPLE_FILES_OPTION, kind, file.getOptions().getJavaMultipleFiles(), false);
            options.add(option);
        }
        return new ProtoFileElement(DEFAULT_LOCATION, packageName, syntax, imports.build(),
                publicImports.build(), types.build(), Collections.emptyList(), Collections.emptyList(),
                options.build());
    }

    private static MessageElement toMessage(FileDescriptorProto file, DescriptorProto descriptor) {
        String name = descriptor.getName();
        ImmutableList.Builder<FieldElement> fields = ImmutableList.builder();
        ImmutableList.Builder<TypeElement> nested = ImmutableList.builder();
        ImmutableList.Builder<ReservedElement> reserved = ImmutableList.builder();
        LinkedHashMap<String, ImmutableList.Builder<FieldElement>> oneofsMap = new LinkedHashMap<>();
        for (OneofDescriptorProto od : descriptor.getOneofDeclList()) {
            oneofsMap.put(od.getName(), ImmutableList.builder());
        }
        List<Map.Entry<String, ImmutableList.Builder<FieldElement>>> oneofs = new ArrayList<>(
                oneofsMap.entrySet());
        for (FieldDescriptorProto fd : descriptor.getFieldList()) {
            if (fd.hasOneofIndex()) {
                FieldElement field = toField(file, fd, true);
                oneofs.get(fd.getOneofIndex()).getValue().add(field);
            } else {
                FieldElement field = toField(file, fd, false);
                fields.add(field);
            }
        }
        for (DescriptorProto nestedDesc : descriptor.getNestedTypeList()) {
            MessageElement nestedMessage = toMessage(file, nestedDesc);
            nested.add(nestedMessage);
        }
        for (EnumDescriptorProto nestedDesc : descriptor.getEnumTypeList()) {
            EnumElement nestedEnum = toEnum(nestedDesc);
            nested.add(nestedEnum);
        }
        for (String reservedName : descriptor.getReservedNameList()) {
            ReservedElement reservedElem = new ReservedElement(DEFAULT_LOCATION, "",
                    Collections.singletonList(reservedName));
            reserved.add(reservedElem);
        }
        ImmutableList.Builder<OptionElement> options = ImmutableList.builder();
        if (descriptor.getOptions().hasMapEntry()) {
            OptionElement.Kind kind = OptionElement.Kind.BOOLEAN;
            OptionElement option = new OptionElement(MAP_ENTRY_OPTION, kind, descriptor.getOptions().getMapEntry(),
                    false);
            options.add(option);
        }
        return new MessageElement(DEFAULT_LOCATION, name, "", nested.build(), options.build(),
                reserved.build(), fields.build(),
                oneofs.stream().map(e -> toOneof(e.getKey(), e.getValue())).collect(Collectors.toList()),
                Collections.emptyList(), Collections.emptyList());
    }

    private static OneOfElement toOneof(String name, ImmutableList.Builder<FieldElement> fields) {
        return new OneOfElement(name, "", fields.build(), Collections.emptyList(), Collections.emptyList());
    }

    private static EnumElement toEnum(EnumDescriptorProto ed) {
        String name = ed.getName();
        ImmutableList.Builder<EnumConstantElement> constants = ImmutableList.builder();
        for (EnumValueDescriptorProto ev : ed.getValueList()) {
            ImmutableList.Builder<OptionElement> options = ImmutableList.builder();
            constants.add(new EnumConstantElement(DEFAULT_LOCATION, ev.getName(), ev.getNumber(), "",
                    options.build()));
        }
        ImmutableList.Builder<OptionElement> options = ImmutableList.builder();
        if (ed.getOptions().hasAllowAlias()) {
            OptionElement.Kind kind = OptionElement.Kind.BOOLEAN;
            OptionElement option = new OptionElement(ALLOW_ALIAS_OPTION, kind, ed.getOptions().getAllowAlias(),
                    false);
            options.add(option);
        }
        return new EnumElement(DEFAULT_LOCATION, name, "", options.build(), constants.build());
    }

    private static FieldElement toField(FileDescriptorProto file, FieldDescriptorProto fd, boolean inOneof) {
        String name = fd.getName();
        ImmutableList.Builder<OptionElement> options = ImmutableList.builder();
        if (fd.getOptions().hasPacked()) {
            OptionElement.Kind kind = OptionElement.Kind.BOOLEAN;
            OptionElement option = new OptionElement(PACKED_OPTION, kind, fd.getOptions().getPacked(), false);
            options.add(option);
        }
        if (fd.getOptions().hasDeprecated()) {
            OptionElement.Kind kind = OptionElement.Kind.BOOLEAN;
            OptionElement option = new OptionElement(DEPRECATED_OPTION, kind, fd.getOptions().getDeprecated(), false);
            options.add(option);
        }
//        if (fd.hasJsonName()) {
//            OptionElement.Kind kind = OptionElement.Kind.STRING;
//            OptionElement option = new OptionElement(JSON_NAME_OPTION, kind, fd.getJsonName(), false);
//            options.add(option);
//        }
        //Implicitly jsonName to null as Options is already setting it. Setting it here results in duplicate json_name
        //option in inferred schema.
        String jsonName = fd.hasJsonName() ? fd.getJsonName() : null;
        String defaultValue = fd.hasDefaultValue() && fd.getDefaultValue() != null ? fd.getDefaultValue()
                : null;
        return new FieldElement(DEFAULT_LOCATION, inOneof ? null : label(file, fd), dataType(fd), name,
                defaultValue, jsonName, fd.getNumber(), "", options.build());
    }

    private static Field.Label label(FileDescriptorProto file, FieldDescriptorProto fd) {
        boolean isProto3 = file.getSyntax().equals(PROTO3);
        switch (fd.getLabel()) {
            case LABEL_REQUIRED:
                return isProto3 ? null : Field.Label.REQUIRED;
            case LABEL_OPTIONAL:
                return Field.Label.OPTIONAL;
            case LABEL_REPEATED:
                return Field.Label.REPEATED;
            default:
                throw new IllegalArgumentException("Unsupported label");
        }
    }

    private static String dataType(FieldDescriptorProto field) {
        if (field.hasTypeName()) {
            return field.getTypeName();
        } else {
            FieldDescriptorProto.Type type = field.getType();
            return FieldDescriptor.Type.valueOf(type).name().toLowerCase();
        }
    }

}
