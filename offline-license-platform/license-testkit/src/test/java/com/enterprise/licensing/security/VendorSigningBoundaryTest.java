package com.enterprise.licensing.security;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class VendorSigningBoundaryTest {
 @Test void customerRuntimeHasNoVendorSigningDependencyOrImport()throws Exception{
  Path root=Path.of("..").toAbsolutePath().normalize();
  for(String module:java.util.List.of("license-server","license-client-java","license-engine","license-storage")){
   String pom=Files.readString(root.resolve(module).resolve("pom.xml"));
   if(module.equals("license-server"))assertThat(pom).contains("<artifactId>license-crypto-vendor</artifactId><version>${project.version}</version><scope>test</scope>");
   else assertThat(pom).doesNotContain("license-crypto-vendor");
   Path main=root.resolve(module).resolve("src/main");if(Files.exists(main))try(var files=Files.walk(main)){for(Path file:files.filter(Files::isRegularFile).toList())assertThat(Files.readString(file)).as(file.toString()).doesNotContain("com.enterprise.licensing.crypto.vendor");}
  }
 }
}
