/*
 *  Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
package org.ballerinalang.test.object;

import org.ballerinalang.model.values.BError;
import org.ballerinalang.model.values.BInteger;
import org.ballerinalang.model.values.BMap;
import org.ballerinalang.model.values.BValue;
import org.ballerinalang.test.util.BAssertUtil;
import org.ballerinalang.test.util.BCompileUtil;
import org.ballerinalang.test.util.BRunUtil;
import org.ballerinalang.test.util.CompileResult;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.ballerinalang.compiler.util.TypeTags;

/**
 * Test cases for object initializer feature.
 */
public class ObjectInitializerTest {
    private CompileResult compileResult;

    @BeforeClass
    public void setup() {
        compileResult = BCompileUtil.compile(this, "test-src/object", "init");
    }

    @Test(description = "Test object initializers that are in the same package")
    public void testStructInitializerInSamePackage1() {
        BValue[] returns = BRunUtil.invoke(compileResult, "testObjectInitializerInSamePackage1");

        Assert.assertEquals(((BInteger) returns[0]).intValue(), 10);
        Assert.assertEquals(returns[1].stringValue(), "Peter");
    }

    @Test(description = "Test object initializers that are in different packages")
    public void testStructInitializerInAnotherPackage() {
        BValue[] returns = BRunUtil.invoke(compileResult, "testObjectInitializerInAnotherPackage");

        Assert.assertEquals(((BInteger) returns[0]).intValue(), 10);
        Assert.assertEquals(returns[1].stringValue(), "Peter");
    }

    @Test(description = "Test object initializer order, 1) default values, 2) initializer, 3) literal ")
    public void testStructInitializerOrder() {
        BValue[] returns = BRunUtil.invoke(compileResult, "testObjectInitializerOrder");

        Assert.assertEquals(((BInteger) returns[0]).intValue(), 40);
        Assert.assertEquals(returns[1].stringValue(), "AB");
    }

    @Test(description = "Test negative object initializers scenarios")
    public void testInvalidStructLiteralKey() {
        CompileResult result = BCompileUtil.compile(this, "test-src/object", "init.negative");
        Assert.assertEquals(result.getErrorCount(), 1);
        BAssertUtil.validateError(result, 0, "attempt to refer to non-accessible symbol 'student.__init'", 5, 21);

    }

    @Test(description = "Test negative object initializers scenarios")
    public void testObjectInitializerNegatives() {
        CompileResult result = BCompileUtil.compile("test-src/object/object_initializer_negative.bal");
        Assert.assertEquals(result.getErrorCount(), 2);
        BAssertUtil.validateError(result, 0, "redeclared symbol 'Foo.__init'", 7, 14);
        BAssertUtil.validateError(result, 1,
                                  "object initializer function can not be declared as private", 11, 4);

    }

    @Test(description = "Test object initializer invocation")
    public void testObjectInitializerUsedAsAFunction() {
        BValue[] returns = BRunUtil.invoke(compileResult, "testObjectInitializerUsedAsAFunction");

        Assert.assertEquals(((BInteger) returns[0]).intValue(), 20);
        Assert.assertEquals(returns[1].stringValue(), "James");
        Assert.assertEquals(((BInteger) returns[2]).intValue(), 10);
        Assert.assertEquals(returns[3].stringValue(), "Peter");
    }

    @Test(description = "Test error returning object initializer invocation")
    public void testErrorReturningInitializer() {
        BValue[] returns = BRunUtil.invoke(compileResult, "testErrorReturningInitializer");

        Assert.assertEquals(returns[0].getType().getTag(), TypeTags.ERROR);
        Assert.assertEquals(((BError) returns[0]).reason, "failed to create Person object");
    }

    @Test(description = "Test initializer with rest args")
    public void testInitializerWithRestArgs() {
        BValue[] returns = BRunUtil.invoke(compileResult, "testInitializerWithRestArgs");
        BMap person = (BMap) returns[0];

        Assert.assertEquals(person.get("name").stringValue(), "Pubudu");
        Assert.assertEquals(((BInteger) person.get("age")).intValue(), 27);
        Assert.assertEquals(person.get("profession").stringValue(), "Software Engineer");
        Assert.assertEquals(person.get("misc").stringValue(), "[{\"city\":\"Colombo\", \"country\":\"Sri Lanka\"}]");
    }

    // TODO: 2/8/19 commented lines due to https://github.com/ballerina-platform/ballerina-lang/issues/13521
    @Test(description = "Test returning a custom error from initializer")
    public void testCustomErrorReturn() {
        BValue[] returns = BRunUtil.invoke(compileResult, "testCustomErrorReturn");

        Assert.assertEquals(returns[0].getType().getTag(), TypeTags.ERROR);
//        Assert.assertEquals(returns[0].getType().getName(), "Err");
        Assert.assertEquals(((BError) returns[0]).reason, "Failed to create object");
        Assert.assertEquals(((BError) returns[0]).details.stringValue(), "{id:100}");

        Assert.assertEquals(returns[1].getType().getTag(), TypeTags.ERROR);
//        Assert.assertEquals(returns[1].getType().getName(), "error");
        Assert.assertEquals(((BError) returns[1]).reason, "Failed to create object");
        Assert.assertEquals(((BError) returns[1]).details.stringValue(), "{id:100}");
    }

    @Test(description = "Test value returned from initializer with a type guard")
    public void testReturnedValWithTypeGuard() {
        BValue[] returns = BRunUtil.invoke(compileResult, "testReturnedValWithTypeGuard");
        Assert.assertEquals(returns[0].stringValue(), "error");
    }

    @Test(description = "Test returning multiple errors from initializer")
    public void testMultipleErrorReturn() {
        BValue[] returns = BRunUtil.invoke(compileResult, "testMultipleErrorReturn");

        Assert.assertEquals(returns[0].getType().getTag(), TypeTags.ERROR);
        Assert.assertEquals(((BError) returns[0]).reason, "Foo Error");
        Assert.assertEquals(((BError) returns[0]).details.stringValue(), "{f:\"foo\"}");

        Assert.assertEquals(returns[1].getType().getTag(), TypeTags.ERROR);
        Assert.assertEquals(((BError) returns[1]).reason, "Bar Error");
        Assert.assertEquals(((BError) returns[1]).details.stringValue(), "{b:\"bar\"}");
    }

    @Test(description = "Test assigning to a variable declared with var")
    public void testAssigningToVar() {
        BValue[] returns = BRunUtil.invoke(compileResult, "testAssigningToVar");

        Assert.assertEquals(returns[0].getType().getTag(), TypeTags.ERROR);
        Assert.assertEquals(((BError) returns[0]).reason, "failed to create Person object");
        Assert.assertEquals(((BError) returns[0]).details.stringValue(), "{\"f\":\"foo\"}");
    }
}
