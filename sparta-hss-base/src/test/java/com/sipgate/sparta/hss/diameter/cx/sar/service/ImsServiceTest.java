package com.sipgate.sparta.hss.diameter.cx.sar.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sipgate.sparta.hss.persistence.ImsiScscfDao;
import com.sipgate.sparta.hss.persistence.LocationIpSmGwDao;
import java.io.IOException;
import jakarta.xml.bind.JAXBException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


@ExtendWith(MockitoExtension.class)
class ImsServiceTest {

    private static final String PRIVATE_ID = "any-private-identity";
    private static final String MSISDN = "any-msisdn";
    @Mock
    private ImsiScscfDao imsiScscfDao;
    @Mock
    private LocationIpSmGwDao locationIpSmGwDao;
    private ImsService underTest;

    private static String createDefaultUserData() {
        return
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><IMSSubscription>"
                + "<PrivateID>" + PRIVATE_ID + "</PrivateID><ServiceProfile>"
                + "<InitialFilterCriteria><ApplicationServer><ServerName>sip:as.example.invalid</ServerName>"
                + "</ApplicationServer><Priority>0</Priority></InitialFilterCriteria><PublicIdentity><Identity>"
                + "tel:+" + MSISDN + "</Identity></PublicIdentity>"
                + "</ServiceProfile></IMSSubscription>";
    }

    @BeforeEach
    void setUp() throws JAXBException, IOException {
        underTest = new ImsService(imsiScscfDao, locationIpSmGwDao, "src/test/resources/userProfile.xml");
    }

    @Test
    void itCreatesUserData() {
        // GIVEN

        // WHEN
        final var userData = underTest.createUserData(PRIVATE_ID, MSISDN);

        // THEN
        assertThat(userData).isEqualTo(createDefaultUserData());
    }
}