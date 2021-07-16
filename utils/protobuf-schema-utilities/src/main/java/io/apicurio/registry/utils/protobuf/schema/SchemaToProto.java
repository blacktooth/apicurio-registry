package io.apicurio.registry.utils.protobuf.schema;

import com.google.protobuf.DescriptorProtos;
import com.squareup.wire.Syntax;
import com.squareup.wire.schema.Field;
import com.squareup.wire.schema.ProtoType;
import com.squareup.wire.schema.internal.parser.EnumConstantElement;
import com.squareup.wire.schema.internal.parser.EnumElement;
import com.squareup.wire.schema.internal.parser.FieldElement;
import com.squareup.wire.schema.internal.parser.MessageElement;
import com.squareup.wire.schema.internal.parser.OneOfElement;
import com.squareup.wire.schema.internal.parser.OptionElement;
import com.squareup.wire.schema.internal.parser.ProtoFileElement;
import com.squareup.wire.schema.internal.parser.ReservedElement;
//import com.squareup.wire.schema.internal.parser.RpcElement;
//import com.squareup.wire.schema.internal.parser.ServiceElement;
//import com.squareup.wire.schema.internal.parser.RpcElement;
import com.squareup.wire.schema.internal.parser.RpcElement;
import com.squareup.wire.schema.internal.parser.ServiceElement;
import com.squareup.wire.schema.internal.parser.TypeElement;
import kotlin.ranges.IntRange;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.google.common.base.CaseFormat.LOWER_UNDERSCORE;
import static com.google.common.base.CaseFormat.UPPER_CAMEL;

public class SchemaToProto {
    private static final String KEY_FIELD = "key";
    private static final String VALUE_FIELD = "value";
    private static final String MAP_ENTRY_SUFFIX = "Entry";
    private static final String JAVA_MULTIPLE_FILES_OPTION = "java_multiple_files";
    private static final String JAVA_OUTER_CLASSNAME_OPTION = "java_outer_classname";
    private static final String JAVA_PACKAGE_OPTION = "java_package";
    private static final String PACKED_OPTION = "packed";
    private static final String JSON_NAME_OPTION = "json_name";
    private static final String PROTO2 = "proto2";
    private static final String PROTO3 = "proto3";
    private static final String ALLOW_ALIAS_OPTION = "allow_alias";
    private static final String MAP_ENTRY_OPTION = "map_entry";
    private static final String DEPRECATED_OPTION = "deprecated";

    public static DescriptorProtos.FileDescriptorProto toFileDescriptorProto(ProtoFileElement element) {
        DescriptorProtos.FileDescriptorProto.Builder schema = DescriptorProtos.FileDescriptorProto.newBuilder();
        schema.setName("default");

        Syntax syntax = element.getSyntax();
        if (syntax != null && syntax != Syntax.PROTO_2) {
            schema.setSyntax(syntax.toString());
        }
        if (element.getPackageName() != null) {
            schema.setPackage(element.getPackageName());
        }

        for (TypeElement typeElem : element.getTypes()) {
            if (typeElem instanceof MessageElement) {
                DescriptorProtos.DescriptorProto message = messageElementToDescriptorProto((MessageElement) typeElem, element);
                schema.addMessageType(message);
            } else if (typeElem instanceof EnumElement) {
                DescriptorProtos.EnumDescriptorProto enumer = enumElementToProto((EnumElement) typeElem);
                schema.addEnumType(enumer);
            }
        }

        //dependencies on protobuf default types are always added
        for (String ref : element.getImports()) {
            schema.addDependency(ref);
        }
        for (String ref : element.getPublicImports()) {
            boolean add = true;
            for (int i = 0; i < schema.getDependencyCount(); i++) {
                if (schema.getDependency(i).equals(ref)) {
                    schema.addPublicDependency(i);
                    add = false;
                }
            }
            if (add) {
                schema.addDependency(ref);
                schema.addPublicDependency(schema.getDependencyCount() - 1);
            }
        }

        String javaPackageName = findOptionString(JAVA_PACKAGE_OPTION, element.getOptions());
        if (javaPackageName != null) {
            DescriptorProtos.FileOptions options = DescriptorProtos.FileOptions.newBuilder()
                .setJavaPackage(javaPackageName)
                .build();
            schema.mergeOptions(options);
        }

        String javaOuterClassname = findOptionString(JAVA_OUTER_CLASSNAME_OPTION, element.getOptions());
        if (javaOuterClassname != null) {
            DescriptorProtos.FileOptions options = DescriptorProtos.FileOptions.newBuilder()
                .setJavaOuterClassname(javaOuterClassname)
                .build();
            schema.mergeOptions(options);
        }

        Boolean javaMultipleFiles = findOptionBoolean(JAVA_MULTIPLE_FILES_OPTION, element.getOptions());
        if (javaMultipleFiles != null) {
            DescriptorProtos.FileOptions options = DescriptorProtos.FileOptions.newBuilder()
                .setJavaMultipleFiles(javaMultipleFiles)
                .build();
            schema.mergeOptions(options);
        }

        //Build services
        for (ServiceElement serviceElement : element.getServices()) {
            DescriptorProtos.ServiceDescriptorProto.Builder serviceBuilder = DescriptorProtos.ServiceDescriptorProto.newBuilder();
            serviceBuilder.setName(serviceElement.getName());
            for (RpcElement serviceMethod : serviceElement.getRpcs()) {
                DescriptorProtos.MethodDescriptorProto.Builder methodBuilder = DescriptorProtos.MethodDescriptorProto.newBuilder();
                methodBuilder.setInputType(serviceMethod.getRequestType());
                methodBuilder.setOutputType(serviceMethod.getResponseType());
                methodBuilder.setName(serviceMethod.getName());
                DescriptorProtos.MethodOptions.Builder methodOptionsBuilder = DescriptorProtos.MethodOptions.newBuilder();
                for (OptionElement serviceMethodOption : serviceMethod.getOptions()) {
                    //Fill options
                }
                methodBuilder.setOptions(methodOptionsBuilder.build());
                if (serviceMethod.getRequestStreaming()) {
                    methodBuilder.setClientStreaming(serviceMethod.getRequestStreaming());
                }
                if (serviceMethod.getResponseStreaming()) {
                    methodBuilder.setServerStreaming(serviceMethod.getResponseStreaming());
                }
                serviceBuilder.addMethod(methodBuilder.build());
            }

            schema.addService( serviceBuilder.build());
        }

        return schema.build();
    }

    private static DescriptorProtos.DescriptorProto messageElementToDescriptorProto(MessageElement messageElem, ProtoFileElement element) {
        ProtobufMessage message = new ProtobufMessage();
        message.protoBuilder().setName(messageElem.getName());

        for (TypeElement type : messageElem.getNestedTypes()) {
            if (type instanceof MessageElement) {
                message.protoBuilder().addNestedType(messageElementToDescriptorProto((MessageElement) type, element));
            } else if (type instanceof EnumElement) {
                message.protoBuilder().addEnumType(enumElementToProto((EnumElement) type));
            }
        }

        Set<String> added = new HashSet<>();

        for (OneOfElement oneof : messageElem.getOneOfs()) {
            DescriptorProtos.OneofDescriptorProto.Builder oneofBuilder = DescriptorProtos.OneofDescriptorProto.newBuilder().setName(oneof.getName());
            message.protoBuilder().addOneofDecl(oneofBuilder);

            for (FieldElement field : oneof.getFields()) {
                String jsonName = findOptionString(JSON_NAME_OPTION, field.getOptions());
                Boolean isDeprecated = findOptionBoolean(DEPRECATED_OPTION, field.getOptions());

                message.addFieldDescriptorProto(
                    DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL,
                    field.getType(),
                    field.getName(),
                    field.getTag(),
                    field.getDefaultValue(),
                    jsonName,
                    null,
                    isDeprecated,
                    message.protoBuilder().getOneofDeclCount() - 1);

                added.add(field.getName());
            }
        }


        // Process fields after messages so that any newly created map entry messages are at the end
        for (FieldElement field : messageElem.getFields()) {
            if (added.contains(field.getName())) {
                continue;
            }
            Field.Label fieldLabel = field.getLabel();
            String label = fieldLabel != null ? fieldLabel.toString().toLowerCase() : "optional";
            String fieldType = field.getType();

            String packageName = element.getPackageName();
            ProtoType protoType = ProtoType.get(fieldType);
            if (!protoType.isScalar() && !protoType.isMap() && protoType.getEnclosingTypeOrPackage() == null) {
                fieldType = ProtoType.get(packageName, fieldType).toString();
            }
            ProtoType keyType = protoType.getKeyType();
            ProtoType valueType = protoType.getValueType();
            // Map fields are only permitted in messages
            if (protoType.isMap() && keyType != null && valueType != null) {
                label = "repeated";
                fieldType = toMapEntry(field.getName());
                ProtobufMessage protobufMapMessage = new ProtobufMessage();
                DescriptorProtos.DescriptorProto.Builder mapMessage = protobufMapMessage
                    .protoBuilder()
                    .setName(fieldType)
                    .mergeOptions(DescriptorProtos.MessageOptions.newBuilder()
                        .setMapEntry(true)
                        .build());

                protobufMapMessage.addField(null, keyType.getSimpleName(), KEY_FIELD, 1, null, null, null, null, null);
                protobufMapMessage.addField(null, valueType.getSimpleName(), VALUE_FIELD, 2, null, null, null, null, null);
                message.protoBuilder().addNestedType(mapMessage.build());
            }

            String jsonName = field.getJsonName();
            Boolean isPacked = findOptionBoolean(PACKED_OPTION, field.getOptions());
            Boolean isDeprecated = findOptionBoolean(DEPRECATED_OPTION, field.getOptions());

            message.addField(label, fieldType, field.getName(), field.getTag(), field.getDefaultValue(), jsonName, isPacked, isDeprecated, null);
        }

        for (ReservedElement reserved : messageElem.getReserveds()) {
            for (Object elem : reserved.getValues()) {
                if (elem instanceof String) {
                    message.protoBuilder().addReservedName((String) elem);
                } else if (elem instanceof Integer) {
                    int tag = (Integer) elem;
                    DescriptorProtos.DescriptorProto.ReservedRange.Builder rangeBuilder = DescriptorProtos.DescriptorProto.ReservedRange.newBuilder()
                        .setStart(tag)
                        .setEnd(tag);
                    message.protoBuilder().addReservedRange(rangeBuilder.build());
                } else if (elem instanceof IntRange) {
                    IntRange range = (IntRange) elem;
                    DescriptorProtos.DescriptorProto.ReservedRange.Builder rangeBuilder = DescriptorProtos.DescriptorProto.ReservedRange.newBuilder()
                        .setStart(range.getStart())
                        .setEnd(range.getEndInclusive());
                    message.protoBuilder().addReservedRange(rangeBuilder.build());
                } else {
                    throw new IllegalStateException(
                        "Unsupported reserved type: " + elem.getClass().getName());
                }
            }
        }
        Boolean isMapEntry = findOptionBoolean(MAP_ENTRY_OPTION, messageElem.getOptions());
        if (isMapEntry != null) {
            DescriptorProtos.MessageOptions.Builder optionsBuilder = DescriptorProtos.MessageOptions.newBuilder()
                .setMapEntry(isMapEntry);
            message.protoBuilder().mergeOptions(optionsBuilder.build());
        }
        return message.build();
    }

    private static DescriptorProtos.EnumDescriptorProto enumElementToProto(EnumElement enumElem) {
        Boolean allowAlias = findOptionBoolean(ALLOW_ALIAS_OPTION, enumElem.getOptions());

        DescriptorProtos.EnumDescriptorProto.Builder builder = DescriptorProtos.EnumDescriptorProto.newBuilder()
            .setName(enumElem.getName());
        if (allowAlias != null) {
            DescriptorProtos.EnumOptions.Builder optionsBuilder = DescriptorProtos.EnumOptions.newBuilder()
                .setAllowAlias(allowAlias);
            builder.mergeOptions(optionsBuilder.build());
        }
        for (EnumConstantElement constant : enumElem.getConstants()) {
            builder.addValue(DescriptorProtos.EnumValueDescriptorProto.newBuilder()
                .setName(constant.getName())
                .setNumber(constant.getTag())
                .build());
        }
        return builder.build();
    }

    private static String toMapEntry(String s) {
        if (s.contains("_")) {
            s = LOWER_UNDERSCORE.to(UPPER_CAMEL, s);
        }
        return s + MAP_ENTRY_SUFFIX;
    }

    private static Optional<OptionElement> findOption(String name, List<OptionElement> options) {
        return options.stream().filter(o -> o.getName().equals(name)).findFirst();
    }

    private static String findOptionString(String name, List<OptionElement> options) {
        return findOption(name, options).map(o -> o.getValue().toString()).orElse(null);
    }

    private static Boolean findOptionBoolean(String name, List<OptionElement> options) {
        return findOption(name, options).map(o -> Boolean.valueOf(o.getValue().toString())).orElse(null);
    }
}
