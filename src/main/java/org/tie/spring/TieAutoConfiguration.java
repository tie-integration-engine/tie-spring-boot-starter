package org.tie.spring;

import java.time.Clock;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.tie.Tie;
import org.tie.spi.DefaultValueResolver;
import org.tie.spi.FieldTransformer;
import org.tie.spi.SecretProvider;

/**
 * Creates a {@link Tie} bean unless the application declares one (§5.6). Every {@link FieldTransformer}
 * bean is registered under its bean name; a {@link DefaultValueResolver}, {@link SecretProvider} or
 * {@link Clock} bean is used if present.
 */
@AutoConfiguration
@EnableConfigurationProperties(TieProperties.class)
public class TieAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public Tie tie(Map<String, FieldTransformer> transformers, ObjectProvider<DefaultValueResolver> defaultValueResolver,
                   ObjectProvider<SecretProvider> secretProvider, ObjectProvider<Clock> clock, TieProperties properties) {
        Tie.Builder builder = Tie.builder();
        transformers.forEach(builder::transformer);
        defaultValueResolver.ifAvailable(builder::defaultValueResolver);
        secretProvider.ifAvailable(builder::secretProvider);
        clock.ifAvailable(builder::clock);
        builder.workingDirectory(properties.workingDirectory());
        return builder.build();
    }
}
