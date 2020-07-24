/*
 *  Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.ballerina.compiler.impl.types;

import org.ballerina.compiler.api.element.ModuleID;
import org.ballerina.compiler.api.type.BallerinaTypeDescriptor;
import org.ballerina.compiler.api.type.TypeDescKind;
import org.ballerina.compiler.impl.semantic.BallerinaTypeDesc;
import org.ballerina.compiler.impl.semantic.TypesFactory;
import org.wso2.ballerinalang.compiler.semantics.model.types.BStreamType;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * Represents an stream type descriptor.
 *
 * @since 1.3.0
 */
public class StreamTypeDescriptor extends BallerinaTypeDesc {

    private List<BallerinaTypeDescriptor> typeParameters;

    public StreamTypeDescriptor(ModuleID moduleID,
                                BStreamType streamType) {
        super(TypeDescKind.STREAM, moduleID, streamType);
    }

    public List<BallerinaTypeDescriptor> getTypeParameters() {
        if (this.typeParameters == null) {
            this.typeParameters = new ArrayList<>();
            typeParameters.add(TypesFactory.getTypeDescriptor(((BStreamType) this.getBType()).constraint));
        }

        return this.typeParameters;
    }

    @Override
    public String signature() {
        String memberSignature;
        if (this.getTypeParameters().isEmpty()) {
            memberSignature = "()";
        } else {
            StringJoiner joiner = new StringJoiner(", ");
            this.getTypeParameters().forEach(typeDescriptor -> joiner.add(typeDescriptor.signature()));
            memberSignature = joiner.toString();
        }
        return "stream<" + memberSignature + ">";
    }
}
