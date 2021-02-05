package io.hotmoka.tests;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.junit.jupiter.api.Test;

import io.takamaka.code.constants.Constants;
import io.takamaka.code.verification.TakamakaClassLoader;
import io.takamaka.code.verification.VerifiedJar;

/**
 * This test tries to verify the same jar twice. This checks
 * that verification does not modify static information of the verified jar,
 * hence it can be applied twice without exceptions.
 */
class DoubleVerification {
	
	@Test
	void verifyTwice() throws IOException, XmlPullParserException {
		// we access the project.version property from the pom.xml file of the parent project
		MavenXpp3Reader reader = new MavenXpp3Reader();
		Model model = reader.read(new FileReader("../pom.xml"));
		String hotmokaVersion = (String) model.getProperties().get("hotmoka.version");
        String takamakaVersion = (String) model.getProperties().get("takamaka.version");
		Path origin = Paths.get("../io-hotmoka-examples/target/io-hotmoka-examples-" + hotmokaVersion + "-lambdas.jar");
		Path classpath = Paths.get("../modules/explicit/io-takamaka-code-" + takamakaVersion + ".jar");
		byte[] bytesOfOrigin = Files.readAllBytes(origin);
		byte[] bytesOfClasspath = Files.readAllBytes(classpath);
    	TakamakaClassLoader classLoader = TakamakaClassLoader.of(Stream.of(bytesOfClasspath, bytesOfOrigin), Constants.DEFAULT_VERIFICATION_VERSION);
    	VerifiedJar.of(bytesOfOrigin, classLoader, false, false);
    	VerifiedJar.of(bytesOfOrigin, classLoader, false, false);
	}
}