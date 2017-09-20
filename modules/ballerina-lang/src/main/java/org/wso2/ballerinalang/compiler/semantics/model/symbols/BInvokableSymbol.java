/*
*  Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
package org.wso2.ballerinalang.compiler.semantics.model.symbols;

import org.ballerinalang.model.symbols.InvokableSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.types.BType;
import org.wso2.ballerinalang.compiler.util.Name;

import java.util.ArrayList;
import java.util.List;

/**
 * @since 0.94
 */
public class BInvokableSymbol extends BSymbol implements InvokableSymbol {

    public List<BVarSymbol> params;
    public List<BVarSymbol> retParams;

    public BInvokableSymbol(int tag,
                            int flags,
                            Name name,
                            BType type,
                            BSymbol owner) {
        super(tag, flags, name, type, owner);
        this.params = new ArrayList<>();
        this.retParams = new ArrayList<>();
    }

    @Override
    public List<BVarSymbol> getParameters() {
        return params;
    }

    @Override
    public List<BVarSymbol> getReturnParameters() {
        return retParams;
    }
}
