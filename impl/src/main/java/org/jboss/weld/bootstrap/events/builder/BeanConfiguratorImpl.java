/*
 * JBoss, Home of Professional Open Source
 * Copyright 2016, Red Hat, Inc., and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.weld.bootstrap.events.builder;

import static org.jboss.weld.util.reflection.Reflections.cast;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.BeanAttributes;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.enterprise.inject.spi.InjectionTarget;
import javax.enterprise.inject.spi.builder.BeanConfigurator;
import javax.enterprise.util.TypeLiteral;

import org.jboss.weld.bootstrap.BeanDeploymentFinder;
import org.jboss.weld.manager.BeanManagerImpl;

/**
 *
 * @author Martin Kouba
 *
 * @param <T>
 */
public class BeanConfiguratorImpl<T> implements BeanConfigurator<T> {

    private BeanManagerImpl beanManager;

    private Class<?> beanClass;

    private final Set<InjectionPoint> injectionPoints;

    private final BeanAttributesConfiguratorImpl<T> attributes;

    private String id;

    private CreateCallback<T> createCallback;

    private DestroyCallback<T> destroyCallback;

    /**
     *
     * @param defaultBeanClass
     * @param beanDeploymentFinder
     */
    public BeanConfiguratorImpl(Class<?> defaultBeanClass, BeanDeploymentFinder beanDeploymentFinder) {
        this.beanClass = defaultBeanClass;
        this.injectionPoints = new HashSet<>();
        this.attributes = new BeanAttributesConfiguratorImpl<>();
        initBeanManager(beanDeploymentFinder);
    }

    @Override
    public BeanConfigurator<T> beanClass(Class<?> beanClass) {
        this.beanClass = beanClass;
        return this;
    }

    @Override
    public BeanConfigurator<T> addInjectionPoint(InjectionPoint injectionPoint) {
        this.injectionPoints.add(injectionPoint);
        return this;
    }

    @Override
    public BeanConfigurator<T> addInjectionPoints(InjectionPoint... injectionPoints) {
        Collections.addAll(this.injectionPoints, injectionPoints);
        return this;
    }

    @Override
    public BeanConfigurator<T> addInjectionPoints(Set<InjectionPoint> injectionPoints) {
        this.injectionPoints.addAll(injectionPoints);
        return this;
    }

    @Override
    public BeanConfigurator<T> injectionPoints(InjectionPoint... injectionPoints) {
        this.injectionPoints.clear();
        return addInjectionPoints(injectionPoints);
    }

    @Override
    public BeanConfigurator<T> injectionPoints(Set<InjectionPoint> injectionPoints) {
        this.injectionPoints.clear();
        return addInjectionPoints(injectionPoints);
    }

    @Override
    public BeanConfigurator<T> id(String id) {
        this.id = id;
        return this;
    }

    @Override
    public <U extends T> BeanConfigurator<U> createWith(Function<CreationalContext<U>, U> callback) {
        this.createCallback = cast(CreateCallback.fromCreateWith(callback));
        return cast(this);
    }

    @Override
    public <U extends T> BeanConfigurator<U> produceWith(Supplier<U> callback) {
        this.createCallback = cast(CreateCallback.fromProduceWith(callback));
        return cast(this);
    }

    @Override
    public <U extends T> BeanConfigurator<U> produceWith(Function<Instance<Object>, U> callback) {
        this.createCallback = cast(CreateCallback.fromProduceWith(callback));
        return cast(this);
    }

    @Override
    public <U extends T> BeanConfigurator<U> producing(U instance) {
        return produceWith(() -> instance);
    }

    @Override
    public BeanConfigurator<T> destroyWith(BiConsumer<T, CreationalContext<T>> callback) {
        this.destroyCallback = new DestroyCallback<>(callback);
        return this;
    }

    @Override
    public BeanConfigurator<T> disposeWith(Consumer<T> callback) {
        this.destroyCallback = new DestroyCallback<>(callback);
        return this;
    }

    @Override
    public <U extends T> BeanConfigurator<U> read(AnnotatedType<U> type) {
        if (beanManager == null) {
            // TODO message
            throw new IllegalStateException();
        }
        // TODO what happens if a new bean class is set after this method?
        final InjectionTarget<T> injectionTarget = cast(beanManager.getInjectionTargetFactory(type).createInjectionTarget(null));
        addInjectionPoints(injectionTarget.getInjectionPoints());
        createWith(c -> {
            T instance = injectionTarget.produce(c);
            injectionTarget.inject(instance, c);
            injectionTarget.postConstruct(instance);
            return instance;
        });
        destroyWith((i, c) -> {
            injectionTarget.preDestroy(i);
            c.release();
        });
        BeanAttributes<U> beanAttributes = beanManager.createBeanAttributes(type);
        read(beanAttributes);
        return cast(this);
    }

    @Override
    public BeanConfigurator<T> read(BeanAttributes<?> beanAttributes) {
        this.attributes.read(beanAttributes);
        return this;
    }

    @Override
    public BeanConfigurator<T> addType(Type type) {
        this.attributes.addType(type);
        return this;
    }

    @Override
    public BeanConfigurator<T> addType(TypeLiteral<?> typeLiteral) {
        this.attributes.addType(typeLiteral.getType());
        return this;
    }

    @Override
    public BeanConfigurator<T> addTypes(Type... types) {
        this.attributes.addTypes(types);
        return this;
    }

    @Override
    public BeanConfigurator<T> addTypes(Set<Type> types) {
        this.attributes.addTypes(types);
        return this;
    }

    @Override
    public BeanConfigurator<T> addTransitiveTypeClosure(Type type) {
        this.attributes.addTransitiveTypeClosure(type);
        return this;
    }

    @Override
    public BeanConfigurator<T> types(Type... types) {
        this.attributes.types(types);
        return this;
    }

    @Override
    public BeanConfigurator<T> types(Set<Type> types) {
        this.attributes.types(types);
        return this;
    }

    @Override
    public BeanConfigurator<T> scope(Class<? extends Annotation> scope) {
        this.attributes.scope(scope);
        return this;
    }

    @Override
    public BeanConfigurator<T> addQualifier(Annotation qualifier) {
        this.attributes.addQualifier(qualifier);
        return this;
    }

    @Override
    public BeanConfigurator<T> addQualifiers(Annotation... qualifiers) {
        this.attributes.addQualifiers(qualifiers);
        return this;
    }

    @Override
    public BeanConfigurator<T> addQualifiers(Set<Annotation> qualifiers) {
        this.attributes.addQualifiers(qualifiers);
        return this;
    }

    @Override
    public BeanConfigurator<T> qualifiers(Annotation... qualifiers) {
        this.attributes.qualifiers(qualifiers);
        return this;
    }

    @Override
    public BeanConfigurator<T> qualifiers(Set<Annotation> qualifiers) {
        this.attributes.qualifiers(qualifiers);
        return this;
    }

    @Override
    public BeanConfigurator<T> addStereotype(Class<? extends Annotation> stereotype) {
        this.attributes.addStereotype(stereotype);
        return this;
    }

    @Override
    public BeanConfigurator<T> addStereotypes(Set<Class<? extends Annotation>> stereotypes) {
        this.attributes.addStereotypes(stereotypes);
        return this;
    }

    @Override
    public BeanConfigurator<T> stereotypes(Set<Class<? extends Annotation>> stereotypes) {
        this.attributes.stereotypes(stereotypes);
        return this;
    }

    @Override
    public BeanConfigurator<T> name(String name) {
        this.attributes.name(name);
        return this;
    }

    @Override
    public BeanConfigurator<T> alternative(boolean alternative) {
        this.attributes.alternative(alternative);
        return this;
    }

    public void initBeanManager(BeanDeploymentFinder beanDeploymentFinder) {
        if (this.beanManager == null && beanDeploymentFinder != null) {
            this.beanManager = beanDeploymentFinder.getOrCreateBeanDeployment(beanClass).getBeanManager();
        }
    }

    Class<?> getBeanClass() {
        return beanClass;
    }

    Set<InjectionPoint> getInjectionPoints() {
        return injectionPoints;
    }

    BeanAttributesConfiguratorImpl<T> getAttributes() {
        return attributes;
    }

    String getId() {
        return id;
    }

    CreateCallback<T> getCreateCallback() {
        return createCallback;
    }

    DestroyCallback<T> getDestroyCallback() {
        return destroyCallback;
    }

    BeanManagerImpl getBeanManager() {
        return beanManager;
    }

    static final class CreateCallback<T> {

        private final Supplier<T> simple;

        private final Function<CreationalContext<T>, T> create;

        private final Function<Instance<Object>, T> instance;

        static <T> CreateCallback<T> fromProduceWith(Function<Instance<Object>, T> callback) {
            return new CreateCallback<T>(null, null, callback);
        }

        static <T> CreateCallback<T> fromProduceWith(Supplier<T> callback) {
            return new CreateCallback<T>(callback, null, null);
        }

        static <T> CreateCallback<T> fromCreateWith(Function<CreationalContext<T>, T> callback) {
            return new CreateCallback<T>(null, callback, null);
        }

        public CreateCallback(Supplier<T> simple, Function<CreationalContext<T>, T> create, Function<Instance<Object>, T> instance) {
            this.simple = simple;
            this.create = create;
            this.instance = instance;
        }

        T create(CreationalContext<T> ctx, BeanManagerImpl beanManager) {
            if (simple != null) {
                return simple.get();
            } else if (instance != null) {
                return instance.apply(beanManager.getInstance(ctx));
            } else {
                return create.apply(ctx);
            }
        }

    }

    static final class DestroyCallback<T> {

        private final BiConsumer<T, CreationalContext<T>> destroy;

        private final Consumer<T> simple;

        public DestroyCallback(Consumer<T> callback) {
            this.destroy = null;
            this.simple = callback;
        }

        public DestroyCallback(BiConsumer<T, CreationalContext<T>> callback) {
            this.destroy = callback;
            this.simple = null;
        }

        void destroy(T instance, CreationalContext<T> ctx) {
            if (simple != null) {
                simple.accept(instance);
            } else {
                destroy.accept(instance, ctx);
            }
        }

    }

}
