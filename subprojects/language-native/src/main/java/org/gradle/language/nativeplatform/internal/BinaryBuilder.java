/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.language.nativeplatform.internal;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.gradle.api.Project;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Usage;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.provider.Providers;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.language.cpp.CppSharedLibrary;
import org.gradle.language.cpp.CppStaticLibrary;
import org.gradle.language.cpp.internal.DefaultUsageContext;
import org.gradle.language.cpp.internal.NativeVariantIdentity;
import org.gradle.language.swift.SwiftSharedLibrary;
import org.gradle.language.swift.SwiftStaticLibrary;
import org.gradle.nativeplatform.Linkage;
import org.gradle.nativeplatform.MachineArchitecture;
import org.gradle.nativeplatform.OperatingSystemFamily;
import org.gradle.nativeplatform.TargetMachine;
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import static org.gradle.language.cpp.CppBinary.DEBUGGABLE_ATTRIBUTE;
import static org.gradle.language.cpp.CppBinary.LINKAGE_ATTRIBUTE;
import static org.gradle.language.cpp.CppBinary.OPTIMIZED_ATTRIBUTE;
import static org.gradle.language.nativeplatform.internal.Dimensions.createDimensionSuffix;

public class BinaryBuilder<T> {
    private final Project project;
    private final ImmutableAttributesFactory attributesFactory;
    private Collection<BuildType> buildTypes = Collections.emptySet();
    private Collection<TargetMachine> targetMachines = Collections.emptySet();
    private Collection<Linkage> linkages = Collections.emptySet();
    private final Map<Class<? extends T>, BinaryFactory<? extends T>> factories = new HashMap<>();
    private Provider<String> baseName = Providers.notDefined();

    public BinaryBuilder(Project project, ImmutableAttributesFactory attributesFactory) {
        this.project = project;
        this.attributesFactory = attributesFactory;
    }

    public BinaryBuilder withBuildTypes(Collection<BuildType> buildTypes) {
        this.buildTypes = buildTypes;
        return this;
    }

    public BinaryBuilder withTargetMachines(Set<TargetMachine> targetMachines) {
        this.targetMachines = targetMachines;
        return this;
    }

    public BinaryBuilder withLinkages(Collection<Linkage> linkages) {
        this.linkages = linkages;
        return this;
    }

    public BinaryBuilder withBaseName(Provider<String> baseName) {
        this.baseName = baseName;
        return this;
    }

    public <I extends T> BinaryBuilder registerBinaryTypeFactory(Class<I> type, BinaryFactory<I> factory) {
        factories.put(type, factory);
        return this;
    }

    public Provider<Set<T>> build() {
        return project.provider(() -> {
            Set<T> binaries = Sets.newHashSet();
            ObjectFactory objectFactory = project.getObjects();
            Usage runtimeUsage = objectFactory.named(Usage.class, Usage.NATIVE_RUNTIME);
            Usage linkUsage = objectFactory.named(Usage.class, Usage.NATIVE_LINK);

            for (BuildType buildType : buildTypes) {
                for (TargetMachine targetMachine : targetMachines) {
                    for (Optional<Linkage> linkage : toOptionals(linkages)) {
                        String operatingSystemSuffix = createDimensionSuffix(targetMachine.getOperatingSystemFamily(), targetMachines);
                        String architectureSuffix = createDimensionSuffix(targetMachine.getArchitecture(), targetMachines);
                        String linkageSuffix = createDimensionSuffix(linkage, linkages);
                        String variantName = buildType.getName() + linkageSuffix + operatingSystemSuffix + architectureSuffix;

                        Provider<String> group = project.provider(new Callable<String>() {
                            @Override
                            public String call() throws Exception {
                                return project.getGroup().toString();
                            }
                        });

                        Provider<String> version = project.provider(new Callable<String>() {
                            @Override
                            public String call() throws Exception {
                                return project.getVersion().toString();
                            }
                        });

                        AttributeContainer runtimeAttributes = attributesFactory.mutable();
                        runtimeAttributes.attribute(Usage.USAGE_ATTRIBUTE, runtimeUsage);
                        runtimeAttributes.attribute(DEBUGGABLE_ATTRIBUTE, buildType.isDebuggable());
                        runtimeAttributes.attribute(OPTIMIZED_ATTRIBUTE, buildType.isOptimized());
                        if (linkage.isPresent()) {
                            runtimeAttributes.attribute(LINKAGE_ATTRIBUTE, linkage.get());
                        }
                        runtimeAttributes.attribute(OperatingSystemFamily.OPERATING_SYSTEM_ATTRIBUTE, targetMachine.getOperatingSystemFamily());
                        runtimeAttributes.attribute(MachineArchitecture.ARCHITECTURE_ATTRIBUTE, targetMachine.getArchitecture());
                        DefaultUsageContext runtimeUsageContext = new DefaultUsageContext(variantName + "-runtime", runtimeUsage, runtimeAttributes);

                        DefaultUsageContext linkUsageContext = null;
                        if (linkage.isPresent()) {
                            AttributeContainer linkAttributes = attributesFactory.mutable();
                            linkAttributes.attribute(Usage.USAGE_ATTRIBUTE, linkUsage);
                            linkAttributes.attribute(DEBUGGABLE_ATTRIBUTE, buildType.isDebuggable());
                            linkAttributes.attribute(OPTIMIZED_ATTRIBUTE, buildType.isOptimized());
                            linkAttributes.attribute(LINKAGE_ATTRIBUTE, linkage.get());
                            linkAttributes.attribute(OperatingSystemFamily.OPERATING_SYSTEM_ATTRIBUTE, targetMachine.getOperatingSystemFamily());
                            linkAttributes.attribute(MachineArchitecture.ARCHITECTURE_ATTRIBUTE, targetMachine.getArchitecture());

                            linkUsageContext = new DefaultUsageContext(variantName + "-link", linkUsage, linkAttributes);
                        }

                        NativeVariantIdentity variantIdentity = new NativeVariantIdentity(variantName, baseName, group, version, buildType.isDebuggable(), buildType.isOptimized(), targetMachine, linkUsageContext, runtimeUsageContext);

                        if (DefaultNativePlatform.getCurrentOperatingSystem().toFamilyName().equals(targetMachine.getOperatingSystemFamily().getName())) {
                            if (linkage.isPresent()) {
                                if (linkage.get().equals(Linkage.SHARED)) {
                                    BinaryFactory<? extends T> factory = factories.get(SwiftSharedLibrary.class);
                                    if (factory == null) {
                                        factory = factories.get(CppSharedLibrary.class);
                                    }
                                    binaries.add(factory.create(variantIdentity, buildType, targetMachine));
                                } else {
                                    BinaryFactory<? extends T> factory = factories.get(SwiftStaticLibrary.class);
                                    if (factory == null) {
                                        factory = factories.get(CppStaticLibrary.class);
                                    }
                                    binaries.add(factory.create(variantIdentity, buildType, targetMachine));
                                }
                            } else {
                                for (BinaryFactory<? extends T> factory : factories.values()) {
                                    binaries.add(factory.create(variantIdentity, buildType, targetMachine));
                                }
                            }
                        }
                    }
                }
            }

            return binaries;
        });
    }

    private static <E> Collection<Optional<E>> toOptionals(Collection<E> collection) {
        if (collection.isEmpty()) {
            return ImmutableSet.of(Optional.empty());
        }
        return collection.stream().map(it -> Optional.of(it)).collect(Collectors.toSet());
    }

    public interface BinaryFactory<T> {
        T create(NativeVariantIdentity variantIdentity, BuildType buildType, TargetMachine targetMachine);
    }
}
