/**
 * Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
 */
define(['log', 'lodash', 'jquery', '../views/backend', 'yaml'],
   function (log, _, $, Backend, YAML) {

       /**
        * @class SwaggerHolder
        * @constructor
        * @class SwaggerHolder
        */
       var SwaggerHolder = function (args) {
           // do nothing
       };

       SwaggerHolder.prototype.constructor = SwaggerHolder;

       SwaggerHolder.prototype.setEditor = function (editor) {
           this._editor = editor;
       };

       SwaggerHolder.prototype.setSwaggerAsText = function (swaggerText) {
           this._apiDoc = YAML.safeDump(YAML.safeLoad(swaggerText));
           this._editor.updateSwaggerEditor();
       };

       SwaggerHolder.prototype.setSwagger = function (apiDoc) {
           this._apiDoc = apiDoc;
       };

       SwaggerHolder.prototype.getSwagger = function () {
           return this._apiDoc;
       };

       return SwaggerHolder;
   });