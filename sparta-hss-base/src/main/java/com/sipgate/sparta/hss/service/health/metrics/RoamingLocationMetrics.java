package com.sipgate.sparta.hss.service.health.metrics;

import static java.util.stream.Collectors.toList;

import com.sipgate.sparta.hss.persistence.ImsiRange;
import com.sipgate.sparta.hss.persistence.LocationLteDao;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import java.util.List;
import java.util.Map;

/// [#updateRoamingLocations()] must be invoked periodically (e.g. once per minute) by the
/// hosting application.
///
/// The eSIM/plastic split follows the configured eSIM IMSI ranges — an operator numbering-plan
/// decision. Without ranges every subscriber counts as plastic.
public class RoamingLocationMetrics {
    private final MultiGauge roamingLocations;
    private final LocationLteDao locationLteDao;
    private final List<ImsiRange> esimImsiRanges;

    public RoamingLocationMetrics(
            final MeterRegistry meterRegistry,
            final LocationLteDao locationLteDao,
            final List<ImsiRange> esimImsiRanges) {
        this.locationLteDao = locationLteDao;
        this.esimImsiRanges = List.copyOf(esimImsiRanges);

        this.roamingLocations = MultiGauge
                .builder("roaming_location")
                .description("Number of sim cards in mobile countries (MCC), mobile networks (MNC) and whether they are esims.")
                .register(meterRegistry);
    }

    public void updateRoamingLocations() {
        roamingLocations.register(
                locationLteDao
                        .getMccMncEsimCounts(esimImsiRanges)
                        .stream()
                        .map(entry ->
                                {
                                    final var country = MCC_TO_ISO3166A3.getOrDefault(entry.mcc(), "(?)");
                                    final var continent = MCC0_TO_CONTINENT.getOrDefault(String.valueOf((entry.mcc()+"?").charAt(0)), "(?)");
                                    return List.of(
                                            MultiGauge.Row.of(
                                                    Tags.of(
                                                            Tag.of("sim_technology", "esim"),
                                                            Tag.of("mcc", entry.mcc()),
                                                            Tag.of("mnc", entry.mnc()),
                                                            Tag.of("country", country),
                                                            Tag.of("continent", continent)
                                                    ),
                                                    entry.cntEsim()
                                            ),
                                            MultiGauge.Row.of(
                                                    Tags.of(
                                                            Tag.of("sim_technology", "plastic"),
                                                            Tag.of("mcc", entry.mcc()),
                                                            Tag.of("mnc", entry.mnc()),
                                                            Tag.of("country", country),
                                                            Tag.of("continent", continent)
                                                    ),
                                                    entry.cntPlastic()
                                            )
                                    );
                                }
                        )
                        .flatMap(List::stream)
                        .collect(toList()),
                true
        );
    }

    /* create like this:
    <pre>
    xmlstarlet \
        sel \
        -N n=https://infocentre.gsm.org/TADIG-RAEX-IR21 \
        --template \
        --match '//n:TADIGSummaryItem/n:NetworkProperties/n:MCC' \
          \
        --value-of '.' \
        --output ', ' \
        --value-of '../../../..//n:CountryInitials' \
          \
          --nl   \
        IR21_*.xml |
        grep -v -w NONE |
        sort -n -t ',' |
        uniq
    </pre>
    Note the manually changed "K00"-> "XKX" for Kosovo, and "AAA"-> "XXX" for unknown countries.
     */
    /** Maps 3-digit MCCs to ISO 3166-1 alpha-3 country codes.
     * <p>
     * Note: Some MCCs are used by multiple countries, e.g. 204 is used by the Netherlands and Singapore.
     * In such cases, the most sensible entry in the map will be used.</p>
     */
    private static final Map<String,String> MCC_TO_ISO3166A3 = Map.<String, String>ofEntries(
        Map.entry("202", "GRC"),
        Map.entry("204", "NLD"),
//        Map.entry("204", "SGP"),
        Map.entry("206", "BEL"),
        Map.entry("208", "FRA"),
        Map.entry("212", "MCO"),
        Map.entry("213", "AND"),
        Map.entry("214", "ESP"),
//        Map.entry("214", "GIB"),
        Map.entry("216", "HUN"),
        Map.entry("218", "BIH"),
        Map.entry("219", "HRV"),
//        Map.entry("220", "MNE"),
        Map.entry("220", "SRB"),
        Map.entry("221", "XKX"), // was: K00, but iso is "XKX"
        Map.entry("222", "ITA"),
        Map.entry("226", "ROU"),
        Map.entry("228", "CHE"),
//        Map.entry("228", "LIE"),
        Map.entry("230", "CZE"),
        Map.entry("231", "SVK"),
        Map.entry("232", "AUT"),
        Map.entry("234", "GBR"),
        Map.entry("238", "DNK"),
        Map.entry("240", "SWE"),
        Map.entry("242", "NOR"),
        Map.entry("244", "FIN"),
        Map.entry("246", "LTU"),
        Map.entry("247", "LVA"),
        Map.entry("248", "EST"),
        Map.entry("250", "RUS"),
        Map.entry("255", "UKR"),
        Map.entry("257", "BLR"),
        Map.entry("259", "MDA"),
        Map.entry("260", "POL"),
        Map.entry("262", "DEU"),
        Map.entry("266", "GIB"),
        Map.entry("268", "PRT"),
        Map.entry("270", "LUX"),
        Map.entry("272", "IRL"),
        Map.entry("274", "ISL"),
        Map.entry("276", "ALB"),
        Map.entry("278", "MLT"),
        Map.entry("280", "CYP"),
        Map.entry("282", "GEO"),
        Map.entry("283", "ARM"),
        Map.entry("284", "BGR"),
        Map.entry("286", "TUR"),
        Map.entry("288", "FRO"),
        Map.entry("290", "GRL"),
        Map.entry("293", "SVN"),
        Map.entry("294", "MKD"),
//        Map.entry("295", "DEU"),
//        Map.entry("295", "IRL"),
        Map.entry("295", "LIE"),
        Map.entry("297", "MNE"),
        Map.entry("302", "CAN"),
        Map.entry("308", "SPM"),
//        Map.entry("310", "GUM"),
        Map.entry("310", "USA"),
//        Map.entry("311", "AAM"),
        Map.entry("311", "USA"),
        Map.entry("312", "USA"),
//        Map.entry("313", "PRI"),
        Map.entry("313", "USA"),
//        Map.entry("314", "CAN"),
        Map.entry("314", "USA"),
        Map.entry("330", "PRI"),
        Map.entry("334", "MEX"),
        Map.entry("338", "JAM"),
//        Map.entry("338", "TCA"),
        Map.entry("340", "GLP"),
        Map.entry("342", "BRB"),
        Map.entry("344", "ATG"),
        Map.entry("346", "CYM"),
        Map.entry("348", "VGB"),
        Map.entry("350", "BMU"),
        Map.entry("352", "GRD"),
        Map.entry("354", "MSR"),
        Map.entry("356", "KNA"),
        Map.entry("358", "LCA"),
        Map.entry("360", "VCT"),
//        Map.entry("362", "ABW"),
        Map.entry("362", "ANT"),
        Map.entry("363", "ABW"),
        Map.entry("364", "BHS"),
        Map.entry("365", "AIA"),
        Map.entry("366", "DMA"),
        Map.entry("370", "DOM"),
        Map.entry("372", "HTI"),
        Map.entry("374", "TTO"),
        Map.entry("376", "TCA"),
        Map.entry("400", "AZE"),
        Map.entry("401", "KAZ"),
        Map.entry("402", "BTN"),
        Map.entry("404", "IND"),
        Map.entry("405", "IND"),
        Map.entry("410", "PAK"),
        Map.entry("412", "AFG"),
        Map.entry("413", "LKA"),
        Map.entry("414", "MMR"),
        Map.entry("415", "LBN"),
        Map.entry("416", "JOR"),
        Map.entry("418", "IRQ"),
        Map.entry("419", "KWT"),
        Map.entry("420", "SAU"),
        Map.entry("422", "OMN"),
        Map.entry("424", "ARE"),
        Map.entry("425", "ISR"),
//        Map.entry("425", "PSE"),
        Map.entry("426", "BHR"),
        Map.entry("427", "QAT"),
        Map.entry("428", "MNG"),
        Map.entry("429", "NPL"),
        Map.entry("434", "UZB"),
//        Map.entry("436", "RUS"),
        Map.entry("436", "TJK"),
        Map.entry("437", "KGZ"),
        Map.entry("440", "JPN"),
        Map.entry("450", "KOR"),
        Map.entry("452", "VNM"),
        Map.entry("454", "HKG"),
        Map.entry("455", "MAC"),
        Map.entry("456", "KHM"),
        Map.entry("457", "LAO"),
        Map.entry("460", "CHN"),
        Map.entry("466", "TWN"),
        Map.entry("470", "BGD"),
        Map.entry("472", "MDV"),
        Map.entry("502", "MYS"),
        Map.entry("505", "AUS"),
        Map.entry("510", "IDN"),
        Map.entry("515", "PHL"),
        Map.entry("520", "THA"),
        Map.entry("525", "SGP"),
        Map.entry("528", "BRN"),
        Map.entry("530", "NZL"),
        Map.entry("536", "NRU"),
        Map.entry("537", "PNG"),
        Map.entry("539", "TON"),
        Map.entry("540", "SLB"),
        Map.entry("541", "VUT"),
        Map.entry("542", "FJI"),
//        Map.entry("542", "TON"),
//        Map.entry("542", "VUT"),
        Map.entry("543", "WLF"),
        Map.entry("544", "ASM"),
        Map.entry("545", "KIR"),
        Map.entry("546", "NCL"),
        Map.entry("547", "PYF"),
        Map.entry("548", "COK"),
        Map.entry("549", "WSM"),
        Map.entry("550", "FSM"),
        Map.entry("552", "PLW"),
        Map.entry("602", "EGY"),
        Map.entry("603", "DZA"),
        Map.entry("604", "MAR"),
        Map.entry("605", "TUN"),
        Map.entry("606", "LBY"),
        Map.entry("607", "GMB"),
        Map.entry("608", "SEN"),
        Map.entry("609", "MRT"),
        Map.entry("610", "MLI"),
        Map.entry("611", "GIN"),
        Map.entry("612", "CIV"),
        Map.entry("613", "BFA"),
        Map.entry("615", "TGO"),
        Map.entry("616", "BEN"),
        Map.entry("617", "MUS"),
        Map.entry("618", "LBR"),
        Map.entry("619", "SLE"),
        Map.entry("620", "GHA"),
        Map.entry("621", "NGA"),
        Map.entry("622", "TCD"),
        Map.entry("624", "CMR"),
        Map.entry("625", "CPV"),
        Map.entry("626", "STP"),
        Map.entry("628", "GAB"),
        Map.entry("629", "COG"),
        Map.entry("630", "COD"),
        Map.entry("631", "AGO"),
        Map.entry("632", "GNB"),
        Map.entry("633", "SYC"),
        Map.entry("634", "SDN"),
        Map.entry("635", "RWA"),
        Map.entry("636", "ETH"),
        Map.entry("637", "SOM"),
        Map.entry("638", "DJI"),
        Map.entry("639", "KEN"),
        Map.entry("640", "TZA"),
        Map.entry("641", "UGA"),
        Map.entry("642", "BDI"),
        Map.entry("643", "MOZ"),
        Map.entry("645", "ZMB"),
        Map.entry("646", "MDG"),
        Map.entry("647", "REU"),
        Map.entry("648", "ZWE"),
        Map.entry("650", "MWI"),
        Map.entry("651", "LSO"),
        Map.entry("652", "BWA"),
        Map.entry("653", "SWZ"),
        Map.entry("655", "ZAF"),
        Map.entry("658", "SHN"),
        Map.entry("659", "SSD"),
        Map.entry("702", "BLZ"),
        Map.entry("704", "GTM"),
        Map.entry("706", "SLV"),
        Map.entry("708", "HND"),
        Map.entry("710", "NIC"),
        Map.entry("712", "CRI"),
        Map.entry("714", "PAN"),
        Map.entry("716", "PER"),
        Map.entry("722", "ARG"),
        Map.entry("724", "BRA"),
//        Map.entry("724", "USA"),
        Map.entry("730", "CHL"),
        Map.entry("732", "COL"),
        Map.entry("734", "VEN"),
        Map.entry("736", "BOL"),
        Map.entry("738", "GUY"),
        Map.entry("740", "ECU"),
        Map.entry("744", "PRY"),
        Map.entry("746", "SUR"),
        Map.entry("748", "URY"),
        Map.entry("883", "SWE"),
//        Map.entry("901", "AAM"),
//        Map.entry("901", "AAQ"),
//        Map.entry("901", "DEU"),
//        Map.entry("901", "ESP"),
//        Map.entry("901", "FRA"),
//        Map.entry("901", "GBR"),
//        Map.entry("901", "IRL"),
//        Map.entry("901", "ITA"),
//        Map.entry("901", "LUX"),
//        Map.entry("901", "MCO"),
//        Map.entry("901", "MLT"),
//        Map.entry("901", "SWE"),
//        Map.entry("901", "UGA"),
//        Map.entry("901", "USA"),
//        Map.entry("994", "AZE")
        Map.entry("901", "XXX")); // was "AAA", but we like "XXX" better

    private static final Map<String,String> MCC0_TO_CONTINENT = Map.<String, String>ofEntries(
            Map.entry("2", "Europe"),
            Map.entry("3", "North America"),
            Map.entry("4", "Asia"),
            Map.entry("5", "Oceania"),
            Map.entry("6", "Africa"),
            Map.entry("7", "South America"),
            Map.entry("9", "World"));

}
