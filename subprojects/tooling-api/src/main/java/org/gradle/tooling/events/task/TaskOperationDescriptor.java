/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.tooling.events.task;

import org.gradle.api.Incubating;
import org.gradle.tooling.events.OperationDescriptor;

import javax.annotation.Nullable;
import java.util.Set;

/**
 * Describes a task operation for which an event has occurred.
 *
 * @since 2.5
 */
public interface TaskOperationDescriptor extends OperationDescriptor {

    /**
     * Returns the path of the task.
     */
    String getTaskPath();

    /**
     * Returns the dependencies of the task, if available.
     *
     * <p>The dependencies are only available for builds that use Gradle 5.1 or later.
     *
     * @return The dependencies of the task, if available; otherwise, {@code null}.
     * @since 5.1
     */
    @Nullable
    @Incubating
    Set<? extends OperationDescriptor> getDependencies();

}
