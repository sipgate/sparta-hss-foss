package com.sipgate.sparta.hss.diameter.cx.sar.service;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.sipgate.sparta.hss.persistence.ImsiScscfDao;
import com.sipgate.sparta.hss.persistence.entities.ImsiScscf;
import com.sipgate.sparta.hss.ims.ImsSubscription;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImsService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImsService.class);

    private final ImsiScscfDao imsiScscfDao;
    private final Marshaller marshaller;
    private final Unmarshaller unmarshaller;
    private final String xmlTemplate;

    public ImsService(
            final ImsiScscfDao imsiScscfDao,
            final String userProfilePath)
            throws JAXBException, IOException {
        this.imsiScscfDao = imsiScscfDao;
        final var context = JAXBContext.newInstance(ImsSubscription.class);
        marshaller = context.createMarshaller();
        unmarshaller = context.createUnmarshaller();
        xmlTemplate = Files.readString(Paths.get(userProfilePath));
    }

    private static String apply(final String template, final Map<String, String> values) {
        var xml = template;
        for (final var entry : values.entrySet()) {
            final var key = entry.getKey();
            final var value = entry.getValue();
            xml = xml.replace("{" + key + "}", value);
        }

        return xml;
    }

    public synchronized String createUserData(final String privateIdentity, final String msisdn) {
        try {
            final Map<String, String> variables = new HashMap<>();
            variables.put("privateId", privateIdentity);
            variables.put("msisdn", msisdn);

            final var xml = apply(xmlTemplate, variables);
            final var imsSubscription = (ImsSubscription) unmarshaller.unmarshal(
                    new ByteArrayInputStream(xml.getBytes(UTF_8)));
            final var stringWriter = new StringWriter();
            marshaller.marshal(imsSubscription, stringWriter);
            return stringWriter.toString();
        } catch (final Exception e) {
            LOGGER.error("Failed to process IMS user profile data", e);
            return null;
        }
    }

    public Optional<String> getScscf(final String imsi) {
        return imsiScscfDao.getScscf(imsi).map(ImsiScscf::getScscf);
    }

    public void setScscf(final String imsi, final String scscf, final String diameterHost, final String diameterRealm) {
        imsiScscfDao.setScscf(imsi, scscf, diameterHost, diameterRealm);
    }

    public void clearScscf(final String imsi) {
        imsiScscfDao.clearScscf(imsi);
    }
}
