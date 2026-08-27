// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.gateway;

import com.cs.common.enums.*;
import com.cs.core.dto.UpdateFirewallRulesRequest;
import com.cs.persistence.dao.FirewallRulesDao;
import com.cs.persistence.entity.FirewallRulesEntity;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Firewall rule service (A6 phase-3 rework):
 *
 * <p>Rules are parsed once into an immutable snapshot (status/mode/address sets) at {@link #refresh()};
 * after loading, {@link #canAccess(String)} is a pure in-memory check with no per-request split/trim.
 * If rules have not been loaded yet (e.g. the metadata DB is down at startup), loading is memoized with failure backoff — within the backoff window an exception is thrown directly
 * (fail-closed, converted by the caller to 503), so requests do not retry the blocking query every time.
 * The blocking load itself must not run on the WebFlux event loop; the caller (gateway filter) is responsible for offloading it to boundedElastic.
 */
@Slf4j
@Service
public class FirewallFilterService {

    @Resource
    private FirewallRulesDao firewallRulesDao;

    /**
     * Backoff interval after a failed (or not yet successful) load, to avoid retrying the blocking query on every request while the metadata DB is down.
     */
    @Value("${datapoly.gateway.firewall.retry-backoff-millis:5000}")
    long retryBackoffMillis;

    volatile LoadedFirewallRules loaded;

    volatile long lastLoadFailedAtMillis = 0L;

    @PostConstruct
    public void init() {
        try {
            refresh();
        } catch (Exception e) {
            log.warn("load firewall rules failed:{}", e.getMessage());
        }
    }

    public void refresh() {
        this.loaded = new LoadedFirewallRules(firewallRulesDao.getFirewallRules());
    }

    /**
     * Whether rules have been loaded successfully (when true, {@link #canAccess(String)} is a pure in-memory check safe to call on the event loop).
     */
    public boolean isLoaded() {
        return null != this.loaded;
    }

    public boolean canAccess(String address) {
        if (null == this.loaded) {
            loadWithBackoff();
        }

        if (OnOffEnum.OFF.equals(loaded.status)) {
            return true;
        }
        if (WhiteBlackEnum.WHITE.equals(loaded.mode)) {
            return loaded.addresses.contains(address);
        } else if (WhiteBlackEnum.BLACK.equals(loaded.mode)) {
            return !loaded.addresses.contains(address);
        } else {
            return false;
        }
    }

    public FirewallRulesEntity getFirewallRules() {
        return (null == this.loaded) ? null : this.loaded.entity;
    }

    public void updateFirewallRules(UpdateFirewallRulesRequest request) {
        firewallRulesDao.update(request.getStatus(), request.getMode(), request.getAddresses());
        this.refresh();
    }

    private void loadWithBackoff() {
        if (System.currentTimeMillis() - lastLoadFailedAtMillis < retryBackoffMillis) {
            throw new IllegalStateException("firewall rules not loaded yet");
        }
        try {
            refresh();
        } catch (RuntimeException e) {
            this.lastLoadFailedAtMillis = System.currentTimeMillis();
            throw e;
        }
    }

    /**
     * Parsed rule snapshot: the entity is kept for management UI display; decision fields and address sets are materialized once at load time.
     */
    static final class LoadedFirewallRules {

        private final FirewallRulesEntity entity;
        private final OnOffEnum status;
        private final WhiteBlackEnum mode;
        private final Set<String> addresses;

        LoadedFirewallRules(FirewallRulesEntity entity) {
            this.entity = entity;
            this.status = (null == entity) ? null : entity.getStatus();
            this.mode = (null == entity) ? null : entity.getMode();
            String lists = (null == entity) ? Strings.EMPTY
                    : Optional.ofNullable(entity.getAddresses()).orElse(Strings.EMPTY);
            this.addresses = Collections.unmodifiableSet(Arrays.asList(lists.split("\n"))
                    .stream().map(String::trim)
                    .filter(StringUtils::isNotBlank)
                    .collect(Collectors.toSet()));
        }

    }

}
