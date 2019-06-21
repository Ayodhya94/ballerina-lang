// Copyright (c) 2018 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
//
// WSO2 Inc. licenses this file to you under the Apache License,
// Version 2.0 (the "License"); you may not use this file except
// in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

# The representation of an inbound authentication handler for HTTP traffic.
public type InboundAuthHandler abstract object {

    # Checks if the request can be authenticated with the relevant `InboundAuthHandler` implementation.
    #
    # + req - The `Request` instanc.
    # + return - Returns `true` if can be authenticated. Else, returns `false`.
    public function canHandle(Request req) returns boolean;

    # Tries to authenticate the request with the relevant `InboundAuthHandler` implementation.
    #
    # + req - `Request` instance
    # + return - `true` if authenticated successfully, else `false` or, `error` in case of errors
    public function handle(Request req) returns boolean|error;
};
