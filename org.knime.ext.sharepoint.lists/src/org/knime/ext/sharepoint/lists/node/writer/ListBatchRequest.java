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
 *   2022-04-04 (jannik.loescher): created
 */
package org.knime.ext.sharepoint.lists.node.writer;

import java.io.IOException;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.StreamSupport;

import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.NodeLogger;
import org.knime.ext.sharepoint.GraphApiUtil.RefreshableAuthenticationProvider;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.microsoft.graph.content.MSBatchRequestContent;
import com.microsoft.graph.core.Constants;
import com.microsoft.graph.http.CustomRequest;
import com.microsoft.graph.http.GraphServiceException;
import com.microsoft.graph.http.HttpMethod;
import com.microsoft.graph.http.IHttpRequest;
import com.microsoft.graph.models.extensions.Entity;
import com.microsoft.graph.models.extensions.IGraphServiceClient;
import com.microsoft.graph.serializer.ISerializer;

/**
 * Helper class to create Graph API batch requests
 *
 * @author Jannik Löscher, KNIME GmbH, Konstanz, Germany
 */
final class ListBatchRequest implements AutoCloseable {
    // Attribute it to the node in the logs
    private static final NodeLogger LOGGER = NodeLogger.getLogger(SharepointListWriterNodeModel.class); // NOSONAR

    private static final int MAX_REQUESTS = MSBatchRequestContent.MAX_NUMBER_OF_REQUESTS;

    private static final JsonObject CONTENT_TYPE_CACHE;
    private static final String[] ID_CACHE;
    private static final JsonArray[] DEPENDS_ON_CACHE;

    /**
     * Amount to wait in seconds after an unknown error occurs in hope that it will
     * vanish after this period.
     */
    private static final long UNKNOWN_ERROR_WAIT = 5;

    /** Wait times in seconds after all batch request were unsuccessful. */
    private static final long[] ALL_UNSUCCESSFUL_WAIT = new long[] { 10, 20, 40, 80, 160 };

    static {
        CONTENT_TYPE_CACHE = new JsonObject();
        CONTENT_TYPE_CACHE.addProperty(Constants.CONTENT_TYPE_HEADER_NAME, Constants.JSON_CONTENT_TYPE);
        DEPENDS_ON_CACHE = new JsonArray[MAX_REQUESTS];
        ID_CACHE = new String[MAX_REQUESTS];
        // IDs should be lexicographically sortable because they are saved as strings
        final var digits = (long) (Math.floor(Math.log10(MAX_REQUESTS))) + 1;
        final var idFormat = "%0" + digits + "d";
        ID_CACHE[0] = String.format(idFormat, 0);
        for (var i = 1; i < MAX_REQUESTS; i++) {
            ID_CACHE[i] = String.format(idFormat, i);
            DEPENDS_ON_CACHE[i] = new JsonArray(1);
            DEPENDS_ON_CACHE[i].add(ID_CACHE[i - 1]);
        }
    }

    private final IGraphServiceClient m_client;
    private final RefreshableAuthenticationProvider m_authProvider;
    private final ExecutionContext m_exec;
    private final int m_absolutePrefixLength;
    private final ISerializer m_serializer;

    private final List<JsonArray> m_results;

    private int m_requestsAccumulated = 0;
    private JsonObject m_body;
    private JsonArray m_requests;
    private List<String> m_contexts;
    private boolean m_errored = false;

    private long m_currentWait;

    /**
     * Create a new batch request handler
     *
     * @param client
     *            the client to create the requests
     * @param authProvider
     *            the AuthenticationProvider to refresh, if neccessary
     * @param exec
     *            the {@link ExecutionContext} to set the messages
     */
    public ListBatchRequest(final IGraphServiceClient client, final RefreshableAuthenticationProvider authProvider,
            final ExecutionContext exec) {
        m_client = client;
        m_authProvider = authProvider;
        m_exec = exec;
        m_serializer = client.getSerializer();

        m_results = new LinkedList<>();
        m_contexts = new LinkedList<>();
        // The current version of the API requires request URLs to be relative to the
        // API root. We get the length of this root by the finding the first part of the
        // custom request.
        m_absolutePrefixLength = createRequest().getRequestUrl().toString().indexOf("/$batch");
    }

    private void checkToken() throws IOException {
        m_authProvider.refreshTokenIfOlder(0);
        LOGGER.debug("Requested new token");
    }

    private CustomRequest<JsonObject> createRequest() {
        return m_client.customRequest("/$batch").buildRequest();
    }

    private JsonObject prepareRequest(final IHttpRequest collectionRequest, final HttpMethod method) {
        if (m_body == null) {
            m_body = new JsonObject();
            m_requests = new JsonArray(MAX_REQUESTS);
            m_body.add("requests", m_requests);
        }
        m_contexts.add(m_exec.getProgressMonitor().getMessage()); // we use the message as context
        final var request = new JsonObject();
        request.addProperty("id", ID_CACHE[m_requestsAccumulated]);
        request.addProperty("url", getRelativeURL(collectionRequest.getRequestUrl().toString()));
        request.addProperty("method", method.name());
        return request;
    }

    /**
     * Enqueue a DELETE request.
     *
     * @param httpRequest
     *            the request to enqueue
     * @param sequential
     *            whether this request should be sequential
     * @throws IOException
     *             if the batch requests or one of its sub-requests encountered an
     *             error that could not be retried while sending.
     * @throws CanceledExecutionException
     *             if the execution was canceled while sending.
     */
    public void delete(final IHttpRequest httpRequest, final boolean sequential)
            throws IOException, CanceledExecutionException {
        postpareRequest(sequential, prepareRequest(httpRequest, HttpMethod.DELETE));
    }

    /**
     * Enqueue a POST request.
     *
     * @param httpRequest
     *            the request to enqueue
     * @param entity
     *            the entity to post
     * @param sequential
     *            whether this request should be sequential
     * @throws IOException
     *             if the batch requests or one of its sub-requests encountered an
     *             error that could not be retried while sending.
     * @throws CanceledExecutionException
     *             if the execution was canceled while sending.
     */
    public void post(final IHttpRequest httpRequest, final Entity entity, final boolean sequential)
            throws IOException, CanceledExecutionException {
        final var request = prepareRequest(httpRequest, HttpMethod.POST);
        request.add("body", JsonParser.parseString(m_serializer.serializeObject(entity)));
        request.add("headers", CONTENT_TYPE_CACHE);
        postpareRequest(sequential, request);
    }

    private void postpareRequest(final boolean forceSequential, final JsonObject request)
            throws IOException, CanceledExecutionException {
        if (forceSequential && m_requestsAccumulated > 0) {
            request.add("dependsOn", DEPENDS_ON_CACHE[m_requestsAccumulated]);
        }
        m_requests.add(request);
        m_requestsAccumulated++;
        if (m_requestsAccumulated >= MAX_REQUESTS) {
            sendRequest();
        }
    }

    private void sendRequest() throws IOException, CanceledExecutionException {
        if (m_body == null || m_requests.size() == 0 || m_requestsAccumulated == 0) {
            return;
        }
        final var retryableErrors = new LinkedList<String>();
        for (final var time : ALL_UNSUCCESSFUL_WAIT) {
            m_exec.checkCanceled();
            final var results = sendAndCollect(retryableErrors);
            if (results.size() > 0) {
                m_results.add(results);
                return;
            } else {
                final var wait = Math.max(time - m_currentWait, 0);
                LOGGER.debug("All requests in batch were unsuccessful! Waiting for " + wait + "s.");
                waitFor("Retrying all requests after", wait);
            }
        }
        LOGGER.errorWithFormat("Errors occured while executing batch request: %s", retryableErrors.toString());
        m_errored = true;
        throw new IOException(String.format("No request could be completed after %d retries! First error: %s",
                ALL_UNSUCCESSFUL_WAIT.length, retryableErrors.getFirst()));
    }

    private JsonArray sendAndCollect(final List<String> retryableErrors)
            throws IOException, CanceledExecutionException {
        final var result = new JsonArray();
        final var errors = new LinkedList<String>();
        m_currentWait = 0;
        try {
            final var response = createRequest().post(m_body).get("responses");
            final var responses = StreamSupport
                    .stream(response.getAsJsonArray().spliterator(), false)//
                    .map(JsonElement::getAsJsonObject)//
                    .sorted(Comparator.comparing(e -> e.get("id").getAsString()))// sort for correct removal
                    .toArray(JsonObject[]::new);
            for (var i = responses.length - 1; i >= 0; i--) {
                handleResponse(result, errors, retryableErrors, i, responses[i]);
            }
        } catch (final GraphServiceException ex) {
            final var status = ResponseStatus.getFromStatusCode(ex.getResponseCode());
            final var error = formatError(ex);
            switch (status) {
            case THROTTLED: // fallthrough
            case SERVICE_UNAVAILABLE: // fallthrough
            case UNKNOWN_ERROR:
                processRetryAfter(ex.getError().rawObject);
                retryableErrors.add(formatError(ex.getError().rawObject));
                break;
            case NON_RETRYABLE_ERROR:
                errors.add(error);
                break;
            case TOKEN_ERROR:
                checkToken();
                // fallthrough
            default:
                retryableErrors.add(error);
            }
        }
        if (!errors.isEmpty()) {
            LOGGER.errorWithFormat("Errors occured while executing batch request: %s", errors.toString());
            m_errored = true;
            throw new IOException(String.format("%d error(s) during execution: %s", errors.size(), errors.get(0)));
        }

        waitFor("Throttled", m_currentWait);
        redistributeIDs();
        if (m_requestsAccumulated > 0) {
            LOGGER.debugWithFormat("Retrying %d request(s) in next batch", m_requestsAccumulated);
        }
        return result;
    }

    private void handleResponse(final JsonArray result, final List<String> nonRetryableErrors,
            final List<String> retryableErrors, final int responseIndex, final JsonObject response) throws IOException {
        final var status = response.get("status").getAsInt();
        switch (ResponseStatus.getFromStatusCode(status)) {
        case SERVICE_UNAVAILABLE:
            // fallthrough
        case THROTTLED:
            processRetryAfter(response);
            retryableErrors.add(formatError(response));
            break;
        case TOKEN_ERROR:
            checkToken();
            // fallthrough
        case FAILED_DEPENDENCY:
            retryableErrors.add(formatError(response));
            break; // just retry and hope for the best
        case UNKNOWN_ERROR:
            processRetryAfter(response);
            m_currentWait = Math.max(m_currentWait, UNKNOWN_ERROR_WAIT);
            retryableErrors.add(formatError(response));
            break;
        case NON_RETRYABLE_ERROR:
            nonRetryableErrors.add(formatError(response));
            // fallthrough
        case SUCCESS:
            result.add(response);
            m_requests.remove(responseIndex);
            m_contexts.remove(responseIndex);
            break;
        default:
            throw new IllegalStateException("Unexpected reponse!");
        }
    }

    private void waitFor(final String cause, long seconds) throws CanceledExecutionException {
        if (seconds <= 0) {
            return;
        }
        final var message = String.format("%s waiting %ds", cause, seconds);
        final var oldMessage = m_exec.getProgressMonitor().getMessage();
        LOGGER.debug(message);
        try {
            while (seconds > 0) {
                m_exec.setMessage(String.format("%s - %s waiting %ds", oldMessage, cause, seconds));
                Thread.sleep(TimeUnit.SECONDS.toMillis(1));
                seconds--;
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            m_errored = true;
            throw new CanceledExecutionException();
        }
        m_exec.setMessage(oldMessage);

    }

    private void redistributeIDs() {
        m_requestsAccumulated = m_requests.size();
        if (m_requestsAccumulated > 0) {
            m_requests.get(0).getAsJsonObject().remove("dependsOn");
        }
        for (var i = 0; i < m_requestsAccumulated; i++) {
            final var request = m_requests.get(i).getAsJsonObject();
            request.addProperty("id", ID_CACHE[i]);
            if (request.has("dependsOn")) {
                request.add("dependsOn", DEPENDS_ON_CACHE[i]);
            }
        }
    }

    private static String formatError(final GraphServiceException ex) {
        return String.format("%s (status: %s, code: %s)", ex.getServiceError().message, ex.getResponseCode(),
                ex.getError().error.code);
    }

    private String formatError(final JsonObject obj) {
        final var status = obj.get("status").getAsInt();
        final var id = Integer.parseInt(obj.get("id").getAsString());
        final var error = obj.getAsJsonObject("body").getAsJsonObject("error");
        return String.format("%s (status: %d, code: %s, context: %s)", error.get("message").getAsString(), status,
                error.get("code").getAsString(), m_contexts.get(id)); // NOSONAR: max MAX_REQUESTS (20)
    }

    private void processRetryAfter(final JsonObject obj) {
        if (obj.has("headers") && obj.getAsJsonObject("headers").has("Retry-After")) {
            m_currentWait = Math.max(m_currentWait,
                    Long.parseLong(obj.getAsJsonObject("headers").get("Retry-After").getAsString()));
        }
    }

    private String getRelativeURL(final String url) {
        return url.substring(m_absolutePrefixLength);
    }

    /**
     * @return the index of this batch in the currently completed batches.
     * @see #tryCompleteAllCurrentRequests()
     */
    public int getCurrentBatchIndex() {
        return m_results.size();
    }

    /**
     * @return get the ID the next enqueud request would get.
     * @see #tryCompleteAllCurrentRequests()
     */
    public String getNextRequestId() {
        return ID_CACHE[m_requestsAccumulated];
    }

    /**
     * Tries to complete any unsuccessful or unsent requests in the current batch
     * and returns all batch results since the last invocation of this method.
     *
     * @return all results of this and the previous invocations of this method. Each
     *         batch request is an array of its responses. The result contains all
     *         previous batch requests (i.e. its an array of arrays of reponses).
     * @throws IOException
     *             if the batch requests or one of its sub-requests encountered an
     *             error that could not be retried.
     * @throws CanceledExecutionException
     *             if the execution was canceled.
     */
    public JsonArray tryCompleteAllCurrentRequests() throws IOException, CanceledExecutionException {
        // this will never create an infinite loop because #sendRequest() will fail
        // after a finite amount of retries
        while (m_requestsAccumulated > 0) {
            sendRequest();
        }
        final var result = new JsonArray(m_results.size());
        for (final var r : m_results) {
            result.add(r);
        }
        m_results.clear();
        return result;
    }

    @Override
    public void close() throws IOException, CanceledExecutionException {
        if (!m_errored) {
            tryCompleteAllCurrentRequests();
        }
    }

    private enum ResponseStatus {
        THROTTLED, TOKEN_ERROR, SERVICE_UNAVAILABLE, FAILED_DEPENDENCY, UNKNOWN_ERROR, NON_RETRYABLE_ERROR, SUCCESS;

        static ResponseStatus getFromStatusCode(final int status) { // NOSONAR: this is nicer this way
            switch (status) {
            case 429: // TOO MANY REQUESTS
                return THROTTLED;
            case 503:
                return SERVICE_UNAVAILABLE;
            case 401: // UNAUTHORISED
                return TOKEN_ERROR;
            case 424:
                return FAILED_DEPENDENCY;
            case 201: // CREATED (fallthrough)
            case 204: // NO CONTENT (Deleted)
                return SUCCESS;
            default:
                // tests below
            }
            if (status >= 500 && status <= 599) { // Server errors
                return UNKNOWN_ERROR;
            }
            return NON_RETRYABLE_ERROR;
        }
    }
}