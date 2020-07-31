package io.takamaka.code.tests;

import io.hotmoka.beans.references.TransactionReference;
import io.hotmoka.beans.signatures.ConstructorSignature;
import io.hotmoka.beans.updates.ClassTag;
import io.hotmoka.beans.values.StorageReference;
import io.hotmoka.network.NodeService;
import io.hotmoka.network.NodeServiceConfig;
import io.hotmoka.network.RemoteNodeConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.security.PrivateKey;

import static io.hotmoka.beans.types.BasicTypes.INT;
import static org.junit.jupiter.api.Assertions.*;

public class RemoteNode extends TakamakaTest {
    private static final BigInteger ALL_FUNDS = BigInteger.valueOf(1_000_000_000);
    private static final BigInteger _20_000 = BigInteger.valueOf(20_000);
    private static final ConstructorSignature CONSTRUCTOR_INTERNATIONAL_TIME = new ConstructorSignature("io.takamaka.tests.basicdependency.InternationalTime", INT, INT, INT);

    private final NodeServiceConfig configNoBanner = new NodeServiceConfig.Builder().setPort(8080).setSpringBannerModeOn(false).build();
    private final RemoteNodeConfig remoteNodeconfig = new RemoteNodeConfig.Builder().setURL("http://localhost:8080").build();


    /**
     * The account that holds all funds.
     */
    private StorageReference master;

    /**
     * The classpath of the classes being tested.
     */
    private TransactionReference classpath;

    /**
     * The private key of {@linkplain #master}.
     */
    private PrivateKey key;

    @BeforeEach
    void beforeEach() throws Exception {
        setNode("basicdependency.jar", ALL_FUNDS, BigInteger.ZERO);
        master = account(0);
        key = privateKey(0);
        classpath = addJarStoreTransaction(key, master, BigInteger.valueOf(10000), BigInteger.ONE, takamakaCode(), bytesOf("basic.jar"), jar());
    }

    @Test
    @DisplayName("starts a network server from a Hotmoka node and makes a remote call to getTakamakaCode")
    void testRemoteTakamakaCode() {

        try (NodeService nodeRestService = NodeService.of(configNoBanner, nodeWithJarsView)) {
            try (io.hotmoka.network.RemoteNode remoteNode = io.hotmoka.network.RemoteNode.of(remoteNodeconfig)) {
                assertNotNull(remoteNode.getTakamakaCode().getHash());
            } catch (Exception e) {
                fail(e.getMessage());
            }
        }
    }

    @Test
    @DisplayName("starts a network server from a Hotmoka node and makes a remote call to getClassTag")
    void testRemoteClassTag() {

        try (NodeService nodeRestService = NodeService.of(configNoBanner, nodeWithJarsView)) {
            try (io.hotmoka.network.RemoteNode remoteNode = io.hotmoka.network.RemoteNode.of(remoteNodeconfig)) {
                ClassTag classTag = remoteNode.getClassTag(master);
                assertEquals(classTag.className, "io.takamaka.code.lang.TestExternallyOwnedAccount");
            } catch (Exception e) {
                fail(e.getMessage());
            }
        }
    }

}
