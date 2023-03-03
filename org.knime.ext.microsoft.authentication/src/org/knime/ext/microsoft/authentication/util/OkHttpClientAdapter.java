/*
 * ------------------------------------------------------------------------
 *
 *  Copyright by KNIME AG, Zurich, Switzerland
 *  Website: http://www.knime.com; Email: contact@knime.com
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License, Version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 *  Additional permission under GNU GPL version 3 section 7:
 *
 *  KNIME interoperates with ECLIPSE solely via ECLIPSE's plug-in APIs.
 *  Hence, KNIME and ECLIPSE are both independent programs and are not
 *  derived from each other. Should, however, the interpretation of the
 *  GNU GPL Version 3 ("License") under any applicable laws result in
 *  KNIME and ECLIPSE being a combined program, KNIME AG herewith grants
 *  you the additional permission to use and propagate KNIME together with
 *  ECLIPSE with only the license terms in place for ECLIPSE applying to
 *  ECLIPSE and the GNU GPL Version 3 applying for KNIME, provided the
 *  license terms of ECLIPSE themselves allow for the respective use and
 *  propagation of ECLIPSE together with KNIME.
 *
 *  Additional permission relating to nodes for KNIME that extend the Node
 *  Extension (and in particular that are based on subclasses of NodeModel,
 *  NodeDialog, and NodeView) and that only interoperate with KNIME through
 *  standard APIs ("Nodes"):
 *  Nodes are deemed to be separate and independent programs and to not be
 *  covered works.  Notwithstanding anything to the contrary in the
 *  License, the License does not apply to Nodes, you are not required to
 *  license Nodes under the License, and you are granted a license to
 *  prepare and propagate Nodes, in each case even if such Nodes are
 *  propagated with or for interoperation with KNIME.  The owner of a Node
 *  may freely choose the license terms applicable to such Node, including
 *  when such Node is propagated with or for interoperation with KNIME.
 * ---------------------------------------------------------------------
 *
 * History
 *   2022-09-04 (Alexander Bondaletov): created
 */
package org.knime.ext.microsoft.authentication.util;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.stream.Collectors;

import com.microsoft.aad.msal4j.HttpRequest;
import com.microsoft.aad.msal4j.HttpResponse;
import com.microsoft.aad.msal4j.IHttpClient;
import com.microsoft.aad.msal4j.IHttpResponse;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * {@link IHttpClient} implementation using {@link OkHttpClient} as a backend.
 * {@link OkHttpClient} instance is configured to support proxy authentication.
 *
 * @author Alexander Bondaletov
 */
public class OkHttpClientAdapter implements IHttpClient {

    private static final String USER_AGENT = String.format("KNIME (%s)", System.getProperty("os.name"));

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    private final OkHttpClient m_client;

    /**
     * Creates new instance.
     */
    public OkHttpClientAdapter() {
        m_client = new OkHttpClient.Builder() //
                .proxyAuthenticator(new OkHttpProxyAuthenticator()) //
                .connectTimeout(CONNECT_TIMEOUT) //
                .readTimeout(READ_TIMEOUT) //
                .build();
    }

    @SuppressWarnings("resource")
    @Override
    public IHttpResponse send(final HttpRequest httpRequest) throws Exception {
        var okRequest = buildRequest(httpRequest);

        if (okRequest != null) {
            final var response = m_client.newCall(okRequest).execute();
            return buildHttpResponse(response);
        } else {
            return null;
        }
    }

    private static Request buildRequest(final HttpRequest httpRequest) throws IOException {
        final var builder = new Request.Builder() //
                .header("User-Agent", USER_AGENT) //
                .url(httpRequest.url());

        if (httpRequest.headers() != null) {
            for (var entry : httpRequest.headers().entrySet()) {
                if (entry.getValue() != null) {
                    builder.addHeader(entry.getKey(), entry.getValue());
                }
            }
        }

        switch (httpRequest.httpMethod()) {
        case GET:
            builder.get();
            break;
        case POST:
            builder.post(createRequestBody(httpRequest));
            break;
        default:
            return null;
        }

        return builder.build();
    }

    private static RequestBody createRequestBody(final HttpRequest request) throws IOException {
        var baOut = new ByteArrayOutputStream();
        try (var wr = new DataOutputStream(baOut)) {
            wr.writeBytes(request.body());
            wr.flush();
            return RequestBody.create(null, baOut.toByteArray());
        }
    }

    @SuppressWarnings("resource")
    private static IHttpResponse buildHttpResponse(final Response response) throws IOException {
        final var httpResponse = new HttpResponse().statusCode(response.code());

        if (response.body() != null) {
            httpResponse.body(response.body().string());
        } else {
            httpResponse.body("");
        }

        final var headers = response.headers()//
                .names()//
                .stream()//
                .collect(Collectors.toMap(n -> n, response::headers));
        httpResponse.addHeaders(headers);
        return httpResponse;
    }
}
