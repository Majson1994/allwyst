package pl.allegrolister.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CryptoKeyInitializer {

    private static final Logger log = LoggerFactory.getLogger(CryptoKeyInitializer.class);

    public CryptoKeyInitializer(AppProperties props) {
        String secret = props.getEncryptionKey();
        if (secret == null || secret.isBlank() || secret.startsWith("zmien")) {
            log.warn("Używasz domyślnego klucza szyfrowania tokenów. Ustaw APP_ENCRYPTION_KEY na produkcji!");
        }
        CryptoConverter.init(secret == null || secret.isBlank() ? "zmien-ten-klucz-na-produkcji" : secret);
    }
}
