package com.sipgate.sparta.hss.ims;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ImsSubscriptionTest {

    private Marshaller marshaller;
    private Unmarshaller unmarshaller;

    @BeforeEach
    void setUp() throws JAXBException {
        unmarshaller = JAXBContext.newInstance(ImsSubscription.class).createUnmarshaller();
        marshaller = JAXBContext.newInstance(ImsSubscription.class).createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
    }

    @Test
    void xmlIsTheSameAfterLoadingAndSaving() throws JAXBException, IOException, URISyntaxException {
        // GIVEN
        final var systemResource = ClassLoader.getSystemResource("ims/IMSSubscription.xml");
        final var actual = (ImsSubscription) unmarshaller.unmarshal(systemResource);

        // WHEN
        final var stringWriter = new StringWriter();
        marshaller.marshal(actual, stringWriter);

        // THEN
        final var expected = Files.readString(Path.of(systemResource.toURI()));
        assertThat(stringWriter).hasToString(expected);
    }
}
