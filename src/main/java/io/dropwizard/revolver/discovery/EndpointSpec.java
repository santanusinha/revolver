/*
 * Copyright 2016 Phaneesh Nagaraja <phaneesh.n@gmail.com>.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package io.dropwizard.revolver.discovery;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.dropwizard.revolver.discovery.model.EndpointSpecType;

import javax.validation.constraints.NotNull;

/**
 * @author phaneesh
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type", visible = true)
public abstract class EndpointSpec {

    @NotNull
    private final EndpointSpecType type;

    protected EndpointSpec(final EndpointSpecType type) {
        this.type = type;
    }

    public abstract void accept(final SpecVisitor visitor);

    public EndpointSpecType getType() {
        return this.type;
    }
}
