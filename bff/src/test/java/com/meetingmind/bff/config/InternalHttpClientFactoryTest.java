package com.meetingmind.bff.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundles;

class InternalHttpClientFactoryTest {

    @Test
    void appliesConfiguredSslBundleToInternalClients() throws Exception {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, null, null);
        SslBundle bundle = mock(SslBundle.class);
        SslBundles bundles = mock(SslBundles.class);
        when(bundles.getBundle("meetingmind-internal")).thenReturn(bundle);
        when(bundle.createSslContext()).thenReturn(sslContext);

        InternalHttpClientFactory factory =
                new InternalHttpClientFactory(bundles, " meetingmind-internal ");

        assertThat(factory.newBuilder().build().sslContext()).isSameAs(sslContext);
    }

    @Test
    void leavesLocalHttpClientsOnTheJvmDefaultWhenBundleIsDisabled() {
        SslBundles bundles = mock(SslBundles.class);

        new InternalHttpClientFactory(bundles, " ").newBuilder().build();

        verifyNoInteractions(bundles);
    }
}
