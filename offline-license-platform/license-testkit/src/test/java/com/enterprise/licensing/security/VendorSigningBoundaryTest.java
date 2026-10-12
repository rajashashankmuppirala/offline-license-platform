package com.enterprise.licensing.security;

import java.nio.file.*;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VendorSigningBoundaryTest {
    @Test
    void customerRuntimeHasNoVendorSigningDependencyOrImport() throws Exception {
        Path root = Path.of("..").toAbsolutePath().normalize();
        for (String module : java.util.List.of("license-server", "license-client-java", "license-engine", "license-storage")) {
            var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            var pom = factory.newDocumentBuilder().parse(root.resolve(module).resolve("pom.xml").toFile());
            var dependencies = pom.getElementsByTagName("dependency");
            boolean vendorDependencyPresent = false;
            for (int i = 0; i < dependencies.getLength(); i++) {
                var dependency = (org.w3c.dom.Element) dependencies.item(i);
                if (!"license-crypto-vendor".equals(dependency.getElementsByTagName("artifactId").item(0).getTextContent().trim()))
                    continue;
                vendorDependencyPresent = true;
                assertThat(module).isEqualTo("license-server");
                var scopes = dependency.getElementsByTagName("scope");
                assertThat(scopes.getLength()).as("Vendor signing dependency must explicitly be test-only").isEqualTo(1);
                assertThat(scopes.item(0).getTextContent().trim()).isEqualTo("test");
            }
            if (module.equals("license-server")) assertThat(vendorDependencyPresent).isTrue();
            Path main = root.resolve(module).resolve("src/main");
            if (Files.exists(main)) try (var files = Files.walk(main)) {
                for (Path file : files.filter(Files::isRegularFile).toList())
                    assertThat(Files.readString(file)).as(file.toString()).doesNotContain("com.enterprise.licensing.crypto.vendor");
            }
        }
    }
}
