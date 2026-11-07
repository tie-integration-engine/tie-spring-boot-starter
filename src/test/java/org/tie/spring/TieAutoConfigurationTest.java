package org.tie.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.tie.ConfigurationException;
import org.tie.ExportContext;
import org.tie.ImportContext;
import org.tie.Tie;
import org.tie.config.FieldDefinition;
import org.tie.config.FileFormat;
import org.tie.config.IntegrationDefinition;
import org.tie.config.Layout;
import org.tie.config.LocalDirectoryChannel;
import org.tie.config.SftpChannel;
import org.tie.spi.FieldTransformer;
import org.tie.spi.Outcome;
import org.tie.spi.SecretProvider;

class TieAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TieAutoConfiguration.class));

    @TempDir
    Path dir;

    @Configuration(proxyBeanMethods = false)
    static class Transformers {
        @Bean
        FieldTransformer upperNif() {
            return (value, record, direction) -> value.toUpperCase(Locale.ROOT);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class OwnTie {
        static final Tie TIE = Tie.builder().build();

        @Bean
        Tie myTie() {
            return TIE;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class Components {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-10-26T12:00:00Z"), ZoneOffset.UTC);
        }

        @Bean
        SecretProvider secrets() {
            return name -> "x".toCharArray();
        }
    }

    @Test
    @DisplayName("AC-104 a FieldTransformer bean is registered under its bean name")
    void transformerBean() throws IOException {
        Path in = Files.createDirectories(dir.resolve("in"));
        Files.writeString(in.resolve("altas.csv"), "nif\n12345678z\n");
        IntegrationDefinition definition = IntegrationDefinition.importing("i").inputChannel(LocalDirectoryChannel.of(in))
                .layout(Layout.builder("altas").fileNamePattern("altas").allowedFormats(FileFormat.CSV)
                        .field(FieldDefinition.builder("nif").transformer("upperNif")))
                .build();
        List<Object> values = new ArrayList<>();

        runner.withUserConfiguration(Transformers.class).run(context -> {
            Tie tie = context.getBean(Tie.class);
            assertThat(tie.validate(definition)).isEmpty();
            tie.importFiles(definition, ImportContext.builder(record -> {
                values.add(record.get("nif"));
                return Outcome.APPLIED;
            }).workingDirectory(dir).build());
        });

        assertThat(values).containsExactly("12345678Z");
    }

    @Test
    @DisplayName("AC-105 an application Tie bean replaces the auto-configured one")
    void ownTieBean() {
        runner.withUserConfiguration(OwnTie.class, Transformers.class).run(context -> {
            assertThat(context.getBeansOfType(Tie.class)).containsOnlyKeys("myTie");
            assertThat(context.getBean(Tie.class)).isSameAs(OwnTie.TIE);
        });
    }

    @Test
    void withoutOptionalBeansTheDefaultsApply() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(Tie.class);
            IntegrationDefinition withSecret = IntegrationDefinition.importing("i")
                    .inputChannel(SftpChannel.builder().host("h").username("u").password("${secret:p}")
                            .remoteDirectory("/in").insecureAcceptAnyHostKey(true).build())
                    .layout(Layout.builder("a").fileNamePattern("a").allowedFormats(FileFormat.CSV))
                    .build();
            assertThat(context.getBean(Tie.class).validate(withSecret))
                    .singleElement().asString().contains("no SecretProvider is registered");
        });
    }

    @Test
    void clockAndSecretProviderBeansAreUsed() throws IOException {
        Path out = Files.createDirectories(dir.resolve("out"));
        IntegrationDefinition export = IntegrationDefinition.exporting("e").outputChannel(LocalDirectoryChannel.of(out))
                .outputNamePattern("out_{timestamp:yyyyMMddHHmmss}")
                .layout(Layout.builder("o").allowedFormats(FileFormat.CSV).field(FieldDefinition.builder("a")))
                .build();
        IntegrationDefinition withSecret = IntegrationDefinition.importing("i")
                .inputChannel(SftpChannel.builder().host("h").username("u").password("${secret:p}").remoteDirectory("/in")
                        .insecureAcceptAnyHostKey(true).build())
                .layout(Layout.builder("a").fileNamePattern("a").allowedFormats(FileFormat.CSV))
                .build();

        runner.withUserConfiguration(Components.class).run(context -> {
            Tie tie = context.getBean(Tie.class);
            assertThat(tie.validate(withSecret)).isEmpty();
            assertThat(tie.exportRecords(export, List.of(Map.<String, Object>of("a", "x")).iterator(),
                    ExportContext.builder().workingDirectory(dir).build()).fileName()).isEqualTo("out_20261026120000.csv");
        });
    }

    @Test
    void workingDirectoryProperty() {
        Path missing = dir.resolve("missing-work");
        IntegrationDefinition definition = IntegrationDefinition.importing("i").inputChannel(LocalDirectoryChannel.of(dir))
                .layout(Layout.builder("a").fileNamePattern("a").allowedFormats(FileFormat.CSV)).build();

        runner.withPropertyValues("tie.working-directory=" + missing).run(context -> {
            assertThat(context.getBean(TieProperties.class).workingDirectory()).isEqualTo(missing);
            assertThatThrownBy(() -> context.getBean(Tie.class).importFiles(definition,
                    ImportContext.builder(record -> Outcome.APPLIED).build()))
                    .isInstanceOfSatisfying(ConfigurationException.class, e -> assertThat(e.getProblems())
                            .containsExactly("workingDirectory " + missing + " does not exist"));
        });
    }

    @Test
    void configurationMetadataDescribesTheProperty() throws IOException {
        try (InputStream metadata = getClass().getResourceAsStream("/META-INF/spring-configuration-metadata.json")) {
            assertThat(metadata).isNotNull();
            assertThat(new String(metadata.readAllBytes(), StandardCharsets.UTF_8)).contains("\"tie.working-directory\"");
        }
    }
}
