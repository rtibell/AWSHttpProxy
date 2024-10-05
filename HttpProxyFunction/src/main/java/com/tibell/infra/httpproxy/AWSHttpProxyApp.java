package com.tibell.infra.httpproxy;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.methods.*;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.*;

import java.util.*;

/**
 * Handler for requests to Lambda function.
 */
public class AWSHttpProxyApp implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
        private static final String TARGET_URL = "http://ml3.tibell.io:38080"; // Replace with your target URL
        private static final CloseableHttpClient httpClient = HttpClients.createDefault();
        private static final ObjectMapper mapper = new ObjectMapper();

        @Override
        public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
            LambdaLogger logger = context.getLogger();
            logger.log("Received request: " + request.toString() + "\n");
            logger.log("context: " + context.toString() + "\n");

            try {
                // Construct the target URL
                String path = request.getPath();
                if (path == null) {
                    path = "";
                }
                String queryString = "";

                if (request.getQueryStringParameters() != null && !request.getQueryStringParameters().isEmpty()) {
                    queryString = "?" + request.getQueryStringParameters().entrySet().stream()
                            .map(entry -> entry.getKey() + "=" + entry.getValue())
                            .reduce((a, b) -> a + "&" + b).orElse("");
                }

                String url = TARGET_URL + path + queryString;
                logger.log("Sending request to: " + url + "\n");

                // Create the HTTP request
                HttpRequestBase httpRequest;
                logger.log("Method: " + request.getHttpMethod() + "\n");
                switch (request.getHttpMethod()) {
                    case "GET":
                        httpRequest = new HttpGet(url);
                        break;
                    case "POST":
                        httpRequest = new HttpPost(url);
                        ((HttpPost) httpRequest).setEntity(new StringEntity(request.getBody()));
                        break;
                    case "PUT":
                        httpRequest = new HttpPut(url);
                        ((HttpPut) httpRequest).setEntity(new StringEntity(request.getBody()));
                        break;
                    case "DELETE":
                        httpRequest = new HttpDelete(url);
                        break;
                    default:
                        return new APIGatewayProxyResponseEvent()
                                .withStatusCode(405)
                                .withBody("Method Not Allowed");
                }

                // Set headers
                if (request.getHeaders() != null) {
                    request.getHeaders().forEach((x,y) -> logger.log("Header: " + x + "=" + y + "\n"));
                    request.getHeaders().forEach(httpRequest::setHeader);
                }

                // Execute the request
                logger.log("Executing request " + httpRequest.toString() + "\n");
                CloseableHttpResponse httpResponse = httpClient.execute(httpRequest);
                logger.log("Received response with status code: " + httpResponse.getStatusLine().getStatusCode() + "\n");

                // Read the response
                BufferedReader reader = new BufferedReader(new InputStreamReader(httpResponse.getEntity().getContent()));
                StringBuilder responseBodyBuilder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    responseBodyBuilder.append(line);
                }
                String responseBody = responseBodyBuilder.toString();

                // Prepare the API Gateway response
                Map<String, String> responseHeaders = new HashMap<>();
                Arrays.stream(httpResponse.getAllHeaders()).forEach(header -> responseHeaders.put(header.getName(), header.getValue()));

                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(httpResponse.getStatusLine().getStatusCode())
                        .withHeaders(responseHeaders)
                        .withBody(responseBody);

            } catch (Exception e) {
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(500)
                        .withBody("Internal Server Error: " + e.getMessage());
            }
        }
}
