package org.mdl.smartcare.gateway.security;

import org.mdl.smartcare.gateway.exception.SmartcareBusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.JwkProviderBuilder;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;

import java.security.interfaces.RSAPublicKey;
import java.security.interfaces.ECPublicKey;
import java.net.URI;
import java.util.Map;
import java.util.function.Function;

@Component
public class JwtUtil {

    @Value("${jwt.issuer:issuer}")
    private String issuer;

    @Value("${jwt.audience:audience}")
    private String audience;

    private static final Logger logger = LoggerFactory.getLogger(JwtUtil.class);

    private static final Map<String, Function<Object, Algorithm>> ALGORITHM_MAP = Map.of(
        "RS256", key -> Algorithm.RSA256((RSAPublicKey) key, null),
        "RS384", key -> Algorithm.RSA384((RSAPublicKey) key, null),
        "RS512", key -> Algorithm.RSA512((RSAPublicKey) key, null),
        "ES256", key -> Algorithm.ECDSA256((ECPublicKey) key, null),
        "ES384", key -> Algorithm.ECDSA384((ECPublicKey) key, null),
        "ES512", key -> Algorithm.ECDSA512((ECPublicKey) key, null)
    );

    public Boolean validateToken(String token) {
        try {
            URI uri = new URI(issuer+"/protocol/openid-connect/certs");
            JwkProvider jwkProvider = new JwkProviderBuilder(uri.toURL()).build();

            DecodedJWT decodedJWT = JWT.decode(token);

            // start verification process
            Jwk jwk = jwkProvider.get(decodedJWT.getKeyId());

            // Extract algorithm from JWK dynamically
            String algorithmName = jwk.getAlgorithm();
            Function<Object, Algorithm> algorithmFactory = ALGORITHM_MAP.get(algorithmName);
            
            if (algorithmFactory == null) {
                throw new IllegalArgumentException("Unsupported algorithm: " + algorithmName);
            }
            
            Algorithm algorithm = algorithmFactory.apply(jwk.getPublicKey());

            //verify the token
            JWTVerifier verifier = JWT.require(algorithm)
                    .withIssuer(issuer)
                    .withAudience(audience)
                    .build();
            verifier.verify(decodedJWT);
            
            return true;
        } catch (Exception e) {
            logger.error("Error validating token", e);
            return false;
        }
    }

    public String extractTokenFromHeader(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        
        throw new SmartcareBusinessException("Invalid Authorization header");
    }
}

