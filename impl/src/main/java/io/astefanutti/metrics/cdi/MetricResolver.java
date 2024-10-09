/**
 * Copyright © 2013 Antonin Stefanutti (antonin.stefanutti@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.astefanutti.metrics.cdi;

import io.dropwizard.metrics5.MetricName;
import io.dropwizard.metrics5.MetricRegistry;
import io.dropwizard.metrics5.annotation.CachedGauge;
import io.dropwizard.metrics5.annotation.Counted;
import io.dropwizard.metrics5.annotation.ExceptionMetered;
import io.dropwizard.metrics5.annotation.Gauge;
import io.dropwizard.metrics5.annotation.Metered;
import io.dropwizard.metrics5.annotation.Timed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Vetoed;
import jakarta.inject.Inject;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Member;
import java.lang.reflect.Method;

import static io.astefanutti.metrics.cdi.MetricsParameter.UseAbsoluteName;

@ApplicationScoped
/* package-private */ class MetricResolver {

    @Inject
    private MetricsExtension extension;

    @Inject
    private MetricNameCdi metricNameCdi;

    Of<CachedGauge> cachedGauge(Class<?> topClass, Method method) {
        return resolverOf(topClass, method, CachedGauge.class);
    }

    Of<Counted> counted(Class<?> topClass, Executable executable) {
        return resolverOf(topClass, executable, Counted.class);
    }

    Of<ExceptionMetered> exceptionMetered(Class<?> topClass, Executable executable) {
        return resolverOf(topClass, executable, ExceptionMetered.class);
    }

    Of<Gauge> gauge(Class<?> topClass, Method method) {
        return resolverOf(topClass, method, Gauge.class);
    }

    Of<Metered> metered(Class<?> topClass, Executable executable) {
        return resolverOf(topClass, executable, Metered.class);
    }

    Of<Timed> timed(Class<?> bean, Executable executable) {
        return resolverOf(bean, executable, Timed.class);
    }

    private <T extends Annotation> Of<T> resolverOf(Class<?> bean, Executable executable, Class<T> metric) {
        if (executable.isAnnotationPresent(metric))
            return elementResolverOf(executable, metric);
        else
            return beanResolverOf(executable, metric, bean);
    }

    private <T extends Annotation> Of<T> elementResolverOf(Executable executable, Class<T> metric) {
        T annotation = executable.getAnnotation(metric);
        MetricName name = metricName(executable, metric, metricName(annotation), isMetricAbsolute(annotation));
        return new DoesHaveMetric<>(annotation, name);
    }

    private <T extends Annotation> Of<T> beanResolverOf(Executable executable, Class<T> metric, Class<?> bean) {
        if (bean.isAnnotationPresent(metric)) {
            T annotation = bean.getAnnotation(metric);
            MetricName name = metricName(bean, executable, metric, metricName(annotation), isMetricAbsolute(annotation));
            return new DoesHaveMetric<>(annotation, name);
        } else if (bean.getSuperclass() != null) {
        	return beanResolverOf(executable, metric, bean.getSuperclass());
        }
        return new DoesNotHaveMetric<>();
    }

    // TODO: should be grouped with the metric name strategy
    private MetricName metricName(Executable executable, Class<? extends Annotation> type, String name, boolean absolute) {
        String metric = name.isEmpty() ? defaultName(executable, type) : metricNameCdi.of(name);
        return absolute ? MetricName.build(metric) : MetricRegistry.name(executable.getDeclaringClass(), metric);
    }

    private MetricName metricName(Class<?> bean, Executable executable, Class<? extends Annotation> type, String name, boolean absolute) {
        String metric = name.isEmpty() ? bean.getSimpleName() : metricNameCdi.of(name);
        return absolute ? MetricRegistry.name(metric, defaultName(executable, type)) : MetricRegistry.name(bean.getPackage().getName(), metric, defaultName(executable, type));
    }

    private String defaultName(Executable executable, Class<? extends Annotation> type) {
        if (ExceptionMetered.class.equals(type))
            return MetricRegistry.name(memberName(executable), ExceptionMetered.DEFAULT_NAME_SUFFIX).getKey();
        else
            return memberName(executable);
    }

    // While the Member Javadoc states that the getName method should returns
    // the simple name of the underlying member or constructor, the FQN is returned
    // for constructors. See JDK-6294399:
    // http://bugs.java.com/view_bug.do?bug_id=6294399
    private String memberName(Member member) {
        if (member instanceof Constructor)
            return member.getDeclaringClass().getSimpleName();
        else
            return member.getName();
    }

    private String metricName(Annotation annotation) {
        if (CachedGauge.class.isInstance(annotation))
            return ((CachedGauge) annotation).name();
        else if (Counted.class.isInstance(annotation))
            return ((Counted) annotation).name();
        else if (ExceptionMetered.class.isInstance(annotation))
            return ((ExceptionMetered) annotation).name();
        else if (Gauge.class.isInstance(annotation))
            return ((Gauge) annotation).name();
        else if (Metered.class.isInstance(annotation))
            return ((Metered) annotation).name();
        else if (Timed.class.isInstance(annotation))
            return ((Timed) annotation).name();
        else
            throw new IllegalArgumentException("Unsupported Metrics forMethod [" + annotation.getClass().getName() + "]");
    }

    private boolean isMetricAbsolute(Annotation annotation) {
        if (extension.<Boolean>getParameter(UseAbsoluteName).orElse(false))
            return true;

        if (CachedGauge.class.isInstance(annotation))
            return ((CachedGauge) annotation).absolute();
        else if (Counted.class.isInstance(annotation))
            return ((Counted) annotation).absolute();
        else if (ExceptionMetered.class.isInstance(annotation))
            return ((ExceptionMetered) annotation).absolute();
        else if (Gauge.class.isInstance(annotation))
            return ((Gauge) annotation).absolute();
        else if (Metered.class.isInstance(annotation))
            return ((Metered) annotation).absolute();
        else if (Timed.class.isInstance(annotation))
            return ((Timed) annotation).absolute();
        else
            throw new IllegalArgumentException("Unsupported Metrics forMethod [" + annotation.getClass().getName() + "]");
    }

    interface Of<T extends Annotation> {

        boolean isPresent();

        MetricName metricName();

        T metricAnnotation();
    }

    private static final class DoesHaveMetric<T extends Annotation> implements Of<T> {

        private final T annotation;

        private final MetricName name;

        private DoesHaveMetric(T annotation, MetricName name) {
            this.annotation = annotation;
            this.name = name;
        }

        @Override
        public boolean isPresent() {
            return true;
        }

        @Override
        public MetricName metricName() {
            return name;
        }

        @Override
        public T metricAnnotation() {
            return annotation;
        }
    }

    @Vetoed
    private static final class DoesNotHaveMetric<T extends Annotation> implements Of<T> {

        private DoesNotHaveMetric() {
        }

        @Override
        public boolean isPresent() {
            return false;
        }

        @Override
        public MetricName metricName() {
            throw new UnsupportedOperationException();
        }

        @Override
        public T metricAnnotation() {
            throw new UnsupportedOperationException();
        }
    }
}
