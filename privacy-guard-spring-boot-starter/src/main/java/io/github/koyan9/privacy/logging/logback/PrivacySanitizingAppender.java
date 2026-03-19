/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.logging.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.spi.AppenderAttachable;
import ch.qos.logback.core.spi.AppenderAttachableImpl;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.event.KeyValuePair;

import java.util.List;
import java.util.Map;

public class PrivacySanitizingAppender extends AppenderBase<ILoggingEvent> implements AppenderAttachable<ILoggingEvent> {

    private final AppenderAttachableImpl<ILoggingEvent> appenders = new AppenderAttachableImpl<>();

    @Override
    protected void append(ILoggingEvent eventObject) {
        ILoggingEvent sanitized = sanitize(eventObject);
        appenders.appendLoopOnAppenders(sanitized);
    }

    @Override
    public void start() {
        if (appenders.iteratorForAppenders() == null || !appenders.iteratorForAppenders().hasNext()) {
            addWarn("No attached appender found for PrivacySanitizingAppender");
        }
        super.start();
    }

    private ILoggingEvent sanitize(ILoggingEvent eventObject) {
        Logger logger = resolveLogger(eventObject.getLoggerName());
        LoggingEvent sanitized = new LoggingEvent(
                PrivacySanitizingAppender.class.getName(),
                logger,
                eventObject.getLevel(),
                PrivacyLogbackRuntime.sanitize(eventObject.getFormattedMessage()),
                null,
                null
        );
        sanitized.setLoggerName(eventObject.getLoggerName());
        sanitized.setLoggerContextRemoteView(eventObject.getLoggerContextVO());
        sanitized.setThreadName(eventObject.getThreadName());
        sanitized.setTimeStamp(eventObject.getTimeStamp());
        sanitized.setSequenceNumber(eventObject.getSequenceNumber());
        Map<String, String> mdc = eventObject.getMDCPropertyMap();
        if (mdc != null) {
            sanitized.setMDCPropertyMap(PrivacyLogbackRuntime.sanitizeMdc(mdc));
        }
        List<KeyValuePair> keyValuePairs = eventObject.getKeyValuePairs();
        if (keyValuePairs != null) {
            sanitized.setKeyValuePairs(PrivacyLogbackRuntime.sanitizeKeyValuePairs(keyValuePairs));
        }
        List<Marker> markers = eventObject.getMarkerList();
        if (markers != null) {
            for (Marker marker : markers) {
                sanitized.addMarker(marker);
            }
        }
        if (eventObject.hasCallerData()) {
            sanitized.setCallerData(eventObject.getCallerData());
        }
        if (eventObject.getThrowableProxy() instanceof ThrowableProxy throwableProxy) {
            sanitized.setThrowableProxy(throwableProxy);
        }
        return sanitized;
    }

    private Logger resolveLogger(String loggerName) {
        org.slf4j.Logger logger = LoggerFactory.getLogger(loggerName);
        if (logger instanceof Logger logbackLogger) {
            return logbackLogger;
        }
        throw new IllegalStateException("PrivacySanitizingAppender requires Logback Logger instances");
    }

    @Override
    public void addAppender(Appender<ILoggingEvent> newAppender) {
        appenders.addAppender(newAppender);
    }

    @Override
    public java.util.Iterator<Appender<ILoggingEvent>> iteratorForAppenders() {
        return appenders.iteratorForAppenders();
    }

    @Override
    public Appender<ILoggingEvent> getAppender(String name) {
        return appenders.getAppender(name);
    }

    @Override
    public boolean isAttached(Appender<ILoggingEvent> appender) {
        return appenders.isAttached(appender);
    }

    @Override
    public void detachAndStopAllAppenders() {
        appenders.detachAndStopAllAppenders();
    }

    @Override
    public boolean detachAppender(Appender<ILoggingEvent> appender) {
        return appenders.detachAppender(appender);
    }

    @Override
    public boolean detachAppender(String name) {
        return appenders.detachAppender(name);
    }
}
