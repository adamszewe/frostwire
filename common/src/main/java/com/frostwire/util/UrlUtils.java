/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2020, FrostWire(R). All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.frostwire.util;

import com.frostwire.util.http.HttpClient;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author gubatron
 * @author aldenml
 */
public final class UrlUtils {

    public static final String USUAL_TORRENT_TRACKERS_MAGNET_URL_PARAMETERS = "tr=udp://tracker.leechers-paradise.org:6969/announce&" +
            "tr=udp://tracker.coppersurfer.tk:6969/announce&" +
            "tr=udp://tracker.internetwarriors.net:1337/announce&" +
            "tr=udp://retracker.akado-ural.ru:80/announce&" +
            "tr=udp://tracker.moeking.me:6969/announce&" +
            "tr=udp://carapax.net:6969/announce&" +
            "tr=udp://retracker.baikal-telecom.net:2710/announce&" +
            "tr=udp://bt.dy20188.com:80/announce&" +
            "tr=udp://tracker.nyaa.uk:6969/announce&" +
            "tr=udp://carapax.net:6969/announce&" +
            "tr=udp://amigacity.xyz:6969/announce&" +
            "tr=udp://tracker.supertracker.net:1337/announce&" +
            "tr=udp://tracker.cyberia.is:6969/announce&" +
            "tr=udp://tracker.openbittorrent.com:80/announce&" +
            "tr=udp://tracker.msm8916.com:6969/announce&" +
            "tr=udp://tracker.sktorrent.net:6969/announce&";

    private UrlUtils() {
    }

    public static String encode(String s) {
        if (s == null) {
            return "";
        }
        try {
            return URLEncoder.encode(s, StandardCharsets.UTF_8.name()).replaceAll("\\+", "%20");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return "";
        }
    }

    public static String decode(String s) {
        if (s == null) {
            return "";
        }
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return "";
        }
    }

    public static String buildMagnetUrl(String infoHash, String displayFilename, String trackerParameters) {
        return "magnet:?xt=urn:btih:" + infoHash + "&dn=" + UrlUtils.encode(displayFilename) + "&" + trackerParameters;
    }


    private static class MirrorHeadDuration {
        private final String mirror;
        private long duration;

        public MirrorHeadDuration(String mirror, long duration) {
            this.mirror = mirror;
            this.duration = duration;
        }

        public String mirror() {
            return mirror;
        }

        public long duration() {
            return duration;
        }
    }

    public static long testHeadRequestDurationInMs(final HttpClient httpClient, String url, final int maxWaitInMs) {
        long t_a = System.currentTimeMillis();
        try {
            int httpCode = httpClient.head("https://" + url, maxWaitInMs, null);
            boolean validHttpCode = 100 <= httpCode && httpCode < 400;
            if (!validHttpCode) {
                System.err.println("UrlUtils.testHeadRequestDurationInMs() -> " + url + " errored HTTP " + httpCode + " in " + (System.currentTimeMillis() - t_a) + "ms");
                return maxWaitInMs * 10; // return a big number as to never consider it
            }
        } catch (Throwable t) {
            System.err.println("UrlUtils.testHeadRequestDurationInMs() -> " + url + " errored " + t.getMessage());
        }
        return System.currentTimeMillis() - t_a;
    }

    public static String getFastestMirrorDomain(final HttpClient httpClient, final String[] mirrors, final int minResponseTimeInMs) {
        int httpCode;
        // shuffle mirrors, keep the fastest valid one
        long lowest_delta = minResponseTimeInMs * 10;
        long t_a, t_delta;
        String fastestMirror = null;
        ArrayList<String> mirrorList = new ArrayList(Arrays.asList(mirrors));
        ArrayList<MirrorHeadDuration> mirrorDurations = new ArrayList<>();
        Collections.shuffle(mirrorList);
        final CountDownLatch latch = new CountDownLatch(mirrorList.size());
        ExecutorService executor = Executors.newFixedThreadPool(4);
        for (String randomMirror : mirrorList) {
            executor.submit(() -> {
                mirrorDurations.add(
                        new MirrorHeadDuration(
                                randomMirror,
                                testHeadRequestDurationInMs(httpClient, randomMirror, minResponseTimeInMs)
                        )
                );
                latch.countDown();
            });
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            return mirrors[0];
        }

        mirrorDurations.sort((o1, o2) -> {
            if (o1.duration() < o2.duration()) {
                return -1;
            } else if (o1.duration() > o2.duration()) {
                return 1;
            }
            return 0;
        });

        fastestMirror = mirrorDurations.get(0).mirror();
        System.out.println("UrlUtils.getFastestMirrorDomain() -> fastest mirror is " + fastestMirror + " in " + mirrorDurations.get(0).duration() + "ms");
        return fastestMirror;
    }

    public static String extractDomainName(String url) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            return null;
        }
        return uri.getHost();
    }
}
