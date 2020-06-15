/*
 * Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package org.ballerinalang.model.tree.expressions;

import org.ballerinalang.model.tree.IdentifierNode;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangNamedArgsExpression;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangVariableReference;
import org.wso2.ballerinalang.compiler.tree.types.BLangType;

import java.util.List;

/**
 * Represents a tuple variable reference node.
 * Example:
 * string s;
 * string i;
 * string b;
 * <p>
 * error (s, {i, b}) = error ("Error", {message: "Error Message"});.
 *
 * @since 0.985.0
 */
public interface ErrorVariableReferenceNode extends VariableReferenceNode {

    IdentifierNode getPackageAlias();

    ExpressionNode getMessage();

    List<BLangNamedArgsExpression> getDetail();

    BLangVariableReference getRestVar();

    BLangType getTypeNode();
}
