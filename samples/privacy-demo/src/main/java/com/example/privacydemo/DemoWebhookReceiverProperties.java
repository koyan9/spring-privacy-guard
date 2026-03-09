/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.privacydemo;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "demo.alert.receiver")
public class DemoWebhookReceiverProperties {

    private String bearerToken;
    private String signatureSecret;
    private String signatureAlgorithm = "HmacSHA256";
    private String signatureHeader = "X-Privacy-Alert-Signature";
    private String timestampHeader = "X-Privacy-Alert-Timestamp";
    private String nonceHeader = "X-Privacy-Alert-Nonce";
    private String storeFile;
    private Duration maxSkew = Duration.ofMinutes(5);

    public String getBearerToken() {
        return bearerToken;
    }

    public void setBearerToken(String bearerToken) {
        this.bearerToken = bearerToken;
    }

    public String getSignatureSecret() {
        return signatureSecret;
    }

    public void setSignatureSecret(String signatureSecret) {
        this.signatureSecret = signatureSecret;
    }

    public String getSignatureAlgorithm() {
        return signatureAlgorithm;
    }

    public void setSignatureAlgorithm(String signatureAlgorithm) {
        this.signatureAlgorithm = signatureAlgorithm;
    }

    public String getSignatureHeader() {
        return signatureHeader;
    }

    public void setSignatureHeader(String signatureHeader) {
        this.signatureHeader = signatureHeader;
    }

    public String getTimestampHeader() {
        return timestampHeader;
    }

    public void setTimestampHeader(String timestampHeader) {
        this.timestampHeader = timestampHeader;
    }

    public String getNonceHeader() {
        return nonceHeader;
    }

    public void setNonceHeader(String nonceHeader) {
        this.nonceHeader = nonceHeader;
    }

    public String getStoreFile() {
        return storeFile;
    }

    public void setStoreFile(String storeFile) {
        this.storeFile = storeFile;
    }

    public Duration getMaxSkew() {
        return maxSkew;
    }

    public void setMaxSkew(Duration maxSkew) {
        this.maxSkew = maxSkew;
    }
}
