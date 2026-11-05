package br.com.keyfalls.shared.infrastructure.security;

import br.com.keyfalls.shared.domain.security.PerfilAcesso;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class JwtTokenService {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    public JwtTokenService(JwtEncoder jwtEncoder, JwtProperties jwtProperties, Clock clock) {
        this.jwtEncoder = Objects.requireNonNull(jwtEncoder);
        this.jwtProperties = Objects.requireNonNull(jwtProperties);
        this.clock = Objects.requireNonNull(clock);
    }

    public String emitirTokenAcesso(UUID usuarioId, String email, Set<PerfilAcesso> perfis) {
        Objects.requireNonNull(usuarioId, "usuarioId obrigatorio.");
        Objects.requireNonNull(email, "email obrigatorio.");
        Objects.requireNonNull(perfis, "perfis obrigatorios.");

        Instant agora = Instant.now(clock);
        Instant expiraEm = agora.plus(jwtProperties.accessTokenExpiration());

        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(jwtProperties.issuer())
            .issuedAt(agora)
            .expiresAt(expiraEm)
            .subject(usuarioId.toString())
            .claim("email", email)
            .claim("roles", perfis.stream().map(PerfilAcesso::authority).toList())
            .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
