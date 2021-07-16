package io.apicurio.registry.utils.protobuf.schema;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.squareup.wire.schema.internal.parser.ProtoFileElement;
import com.squareup.wire.schema.internal.parser.ProtoParser;
import io.apicurio.registry.utils.protobuf.schema.syntax2.EmployeeSyntax2OuterClass;
import io.apicurio.registry.utils.protobuf.schema.syntax2.MyOrderingApplicationSyntax2;
import io.apicurio.registry.utils.protobuf.schema.syntax3.EmployeeSyntax3OuterClass;
import io.apicurio.registry.utils.protobuf.schema.syntax3.MyOrderingApplicationSyntax3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class FileDescriptorUtilsTest {

    @Test
    public void fileDescriptorToProtoFile_ParsesJsonNameOptionCorrectly() {
        Descriptors.FileDescriptor fileDescriptor = MyOrderingApplicationSyntax3.getDescriptor().getFile();
        String expectedFieldWithJsonName = "optional string street = 1 [json_name = \"Address_Street\"];\n";
        String expectedFieldWithoutJsonName = "optional int32 zip = 2;\n";

        ProtoFileElement protoFile = FileDescriptorUtils.fileDescriptorToProtoFile(fileDescriptor.toProto());

        String actualSchema = protoFile.toSchema();

        //TODO: Need a better way to compare schema strings.
        assertTrue(actualSchema.contains(expectedFieldWithJsonName));
        assertTrue(actualSchema.contains(expectedFieldWithoutJsonName));
    }

    @Test
    public void fileDescriptorToProtoSchema_ParsesAccuratelyForSyntax2() throws Exception {
        Descriptors.FileDescriptor fileDescriptor = MyOrderingApplicationSyntax2.getDescriptor().getFile();
        DescriptorProtos.FileDescriptorProto fileDescriptorProto = fileDescriptor.toProto();
        String actualSchema = FileDescriptorUtils.fileDescriptorToProtoFile(fileDescriptorProto).toSchema();

        String expectedSchema = ProtobufTestCaseReader.getRawSchema("TestOrderingSyntax2.proto");

        //Convert to Proto and compare
        DescriptorProtos.FileDescriptorProto expectedFileDescriptorProto = schemaTextToFileDescriptor(expectedSchema).toProto();
        DescriptorProtos.FileDescriptorProto actualFileDescriptorProto = schemaTextToFileDescriptor(actualSchema).toProto();

        assertEquals(expectedFileDescriptorProto, actualFileDescriptorProto);
        assertEquals(fileDescriptorProto, expectedFileDescriptorProto);
    }

    @Test
    public void fileDescriptorToProtoSchema_ParsesAccuratelyForSyntax3() throws Exception {
        Descriptors.FileDescriptor fileDescriptor = MyOrderingApplicationSyntax3.getDescriptor().getFile();
        DescriptorProtos.FileDescriptorProto fileDescriptorProto = fileDescriptor.toProto();
        String actualSchema = FileDescriptorUtils.fileDescriptorToProtoFile(fileDescriptorProto).toSchema();

        String expectedSchema = ProtobufTestCaseReader.getRawSchema("TestOrderingSyntax3.proto");

        //Convert to Proto and compare
        DescriptorProtos.FileDescriptorProto expectedFileDescriptorProto = schemaTextToFileDescriptor(expectedSchema).toProto();
        DescriptorProtos.FileDescriptorProto actualFileDescriptorProto = schemaTextToFileDescriptor(actualSchema).toProto();

//        assertEquals(expectedFileDescriptorProto, actualFileDescriptorProto);
        assertEquals(fileDescriptorProto, expectedFileDescriptorProto);
    }

    @Test
    public void fileDescriptorToProtoSchema_ParsesAccuratelyForBasicProto() throws Exception {
        Descriptors.FileDescriptor fileDescriptor = EmployeeSyntax3OuterClass.EmployeeSyntax3.getDescriptor().getFile();
        DescriptorProtos.FileDescriptorProto fileDescriptorProto = fileDescriptor.toProto();
        String actualSchema = FileDescriptorUtils.fileDescriptorToProtoFile(fileDescriptorProto).toSchema();

        String expectedSchema = ProtobufTestCaseReader.getRawSchema("EmployeeSyntax3.proto");

        //Convert to Proto and compare
        DescriptorProtos.FileDescriptorProto expectedFileDescriptorProto = schemaTextToFileDescriptor(expectedSchema).toProto();
        DescriptorProtos.FileDescriptorProto actualFileDescriptorProto = schemaTextToFileDescriptor(actualSchema).toProto();

        assertEquals(expectedFileDescriptorProto, actualFileDescriptorProto);
        assertEquals(fileDescriptorProto, expectedFileDescriptorProto);
    }

    @Test
    public void fileDescriptorToProtoSchema_ParsesAccuratelyForBasicProto2() throws Exception {
        Descriptors.FileDescriptor fileDescriptor = EmployeeSyntax2OuterClass.EmployeeSyntax2.getDescriptor().getFile();
        DescriptorProtos.FileDescriptorProto fileDescriptorProto = fileDescriptor.toProto();
        String actualSchema = FileDescriptorUtils.fileDescriptorToProtoFile(fileDescriptorProto).toSchema();

        String expectedSchema = ProtobufTestCaseReader.getRawSchema("EmployeeSyntax2.proto");

        //Convert to Proto and compare
        DescriptorProtos.FileDescriptorProto expectedFileDescriptorProto = schemaTextToFileDescriptor(expectedSchema).toProto();
        DescriptorProtos.FileDescriptorProto actualFileDescriptorProto = schemaTextToFileDescriptor(actualSchema).toProto();

        assertEquals(expectedFileDescriptorProto, actualFileDescriptorProto);
        assertEquals(fileDescriptorProto, expectedFileDescriptorProto);
    }


    private Descriptors.FileDescriptor schemaTextToFileDescriptor(String schema)
        throws Descriptors.DescriptorValidationException {
        ProtoFileElement fileElem = ProtoParser.Companion.parse(FileDescriptorUtils.DEFAULT_LOCATION, schema);
        return FileDescriptorUtils.protoFileToFileDescriptor(fileElem);
    }

}