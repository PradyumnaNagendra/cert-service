package controllers.certs;

import com.fasterxml.jackson.databind.JsonNode;
import controllers.BaseController;
import controllers.RequestHandler;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.incredible.certProcessor.JsonKey;
import org.sunbird.BaseException;
import org.sunbird.es.ElasticSearchUtil;
import org.sunbird.message.IResponseMessage;
import org.sunbird.message.ResponseCode;
import org.sunbird.request.Request;
import org.sunbird.response.Response;
import play.libs.Json;
import play.mvc.Result;
import play.mvc.Results;
import utils.RequestMapper;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

public class CertTemplateController extends BaseController {

    private static String indexName = "cert-templates";
    private static String docType = "_doc";

    public CompletionStage<Result> create() {
        try {
            Map<String, Object> template = getTemplate(getRequest(request()));
            validateTemplate(template, true);
            String identifier = (String) template.get("identifier");
            if (StringUtils.isBlank(identifier))
                identifier = UUID.randomUUID() + "";
            CompletableFuture<Map<String, Object>> future = ElasticSearchUtil.addDocument(indexName, docType, template, identifier);
            return future.handleAsync((map, exception) -> {
                Response response = new Response();
                if (null != exception) {
                    if (exception instanceof BaseException) {
                        BaseException ex = (BaseException) exception;
                        response.setResponseCode(ResponseCode.BAD_REQUEST);
                        response.put(JsonKey.MESSAGE, ex.getMessage());
                    } else {
                        response.setResponseCode(ResponseCode.SERVER_ERROR);
                        response.put(JsonKey.MESSAGE,locale.getMessage(IResponseMessage.INTERNAL_ERROR,null));
                    }
                } else {
                    response.putAll(map);
                }
                return response;
            }).thenApplyAsync(response -> {
                JsonNode jsonNode = Json.toJson(response);
                if(StringUtils.equalsIgnoreCase(response.getResponseCode().name(), ResponseCode.BAD_REQUEST.name())) {
                    return Results.badRequest(jsonNode);
                } else if (StringUtils.equalsIgnoreCase(response.getResponseCode().name(), ResponseCode.SERVER_ERROR.name())) {
                    return Results.internalServerError(jsonNode);
                }
                return Results.ok(jsonNode);
            });

        } catch (Exception ex) {
            return CompletableFuture.completedFuture(RequestHandler.handleFailureResponse(ex,request()));
        }
    }

    public CompletionStage<Result> update(String identifier) {
        try {
            Map<String, Object> template = getTemplate(getRequest(request()));
            template.put("identifier", identifier);
            validateTemplate(template, false);
            CompletableFuture<Map<String, Object>> future = ElasticSearchUtil.addDocument(indexName, docType, template, identifier);
            return future.handleAsync((map, exception) -> {
                Response response = new Response();
                if (null != exception) {
                    if (exception instanceof BaseException) {
                        BaseException ex = (BaseException) exception;
                        response.setResponseCode(ResponseCode.BAD_REQUEST);
                        response.put(JsonKey.MESSAGE, ex.getMessage());
                    } else {
                        response.setResponseCode(ResponseCode.SERVER_ERROR);
                        response.put(JsonKey.MESSAGE,locale.getMessage(IResponseMessage.INTERNAL_ERROR,null));
                    }
                } else {
                    response.putAll(map);
                }
                return response;
            }).thenApplyAsync(response -> {
                JsonNode jsonNode = Json.toJson(response);
                if(StringUtils.equalsIgnoreCase(response.getResponseCode().name(), ResponseCode.BAD_REQUEST.name())) {
                    return Results.badRequest(jsonNode);
                } else if (StringUtils.equalsIgnoreCase(response.getResponseCode().name(), ResponseCode.SERVER_ERROR.name())) {
                    return Results.internalServerError(jsonNode);
                }
                return Results.ok(jsonNode);
            });

        } catch (Exception ex) {
            return CompletableFuture.completedFuture(RequestHandler.handleFailureResponse(ex,request()));
        }
    }

    public CompletionStage<Result> read(String identifier) {
        return ElasticSearchUtil.getDocument(indexName, docType, identifier)
                .handleAsync((template, exception) -> {
                    Response response = new Response();
                    if (null != exception) {
                        if (exception instanceof BaseException) {
                            BaseException ex = (BaseException) exception;
                            response.setResponseCode(ResponseCode.BAD_REQUEST);
                            response.put(JsonKey.MESSAGE, ex.getMessage());
                        } else {
                            response.setResponseCode(ResponseCode.SERVER_ERROR);
                            response.put(JsonKey.MESSAGE,locale.getMessage(IResponseMessage.INTERNAL_ERROR,null));
                        }
                    } else {
                        if(MapUtils.isNotEmpty(template)) {
                            response.put("certificate", new HashMap<String, Object>() {{
                                put("template", template);
                            }});
                        } else {
                            response.setResponseCode(ResponseCode.RESOURCE_NOT_FOUND);
                            response.put(JsonKey.MESSAGE, "Cert template not found for the given identifier: " + identifier);
                        }
                    }
                    return response;
                }).thenApplyAsync(response -> {
                    JsonNode jsonNode = Json.toJson(response);
                    if(StringUtils.equalsIgnoreCase(response.getResponseCode().name(), ResponseCode.BAD_REQUEST.name())) {
                        return Results.badRequest(jsonNode);
                    } else if (StringUtils.equalsIgnoreCase(response.getResponseCode().name(), ResponseCode.RESOURCE_NOT_FOUND.name())) {
                        return Results.notFound(jsonNode);
                    } else if (StringUtils.equalsIgnoreCase(response.getResponseCode().name(), ResponseCode.SERVER_ERROR.name())) {
                        return Results.internalServerError(jsonNode);
                    }
                    return Results.ok(jsonNode);
                });
    }

    private Request getRequest(play.mvc.Http.Request req) throws Exception {
        Request request = new Request();
        if (req.body() != null && req.body().asJson() != null) {
            request = (Request) RequestMapper.mapRequest(req, Request.class);
        }
        return request;
    }

    private Map<String, Object> getTemplate(Request request) {
        Map<String, Object> cert = (Map<String, Object>) request.getRequest().get("certificate");
        if (MapUtils.isNotEmpty(cert)) {
            Map<String, Object> template = (Map<String, Object>) cert.get("template");
            if (MapUtils.isNotEmpty(template)) {
                return template;
            }
        }
        return null;
    }

    private void validateTemplate(Map<String, Object> template, boolean validateRequired) throws BaseException {
        if (MapUtils.isNotEmpty(template)) {
            List<String> invalidKeys = template.keySet().stream().filter(k -> !Arrays.asList("name", "template", "identifier", "params").contains(k)).collect(Collectors.toList());
            if(invalidKeys.size() > 0) {
                throw new BaseException("CLIENT_ERROR", "template has invalid properties: " + invalidKeys, ResponseCode.BAD_REQUEST.getCode());
            }
            if (validateRequired && !template.keySet().containsAll(Arrays.asList("name", "template")))
                throw new BaseException("CLIENT_ERROR", "name or template missing in request.", ResponseCode.BAD_REQUEST.getCode());
        } else {
            throw new BaseException("CLIENT_ERROR", "Request is empty.", ResponseCode.BAD_REQUEST.getCode());
        }
    }

    private CompletionStage<Result> returnResponse(Map<String, Object> result) {
        Response response = new Response();
        response.putAll(result);
        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        future.complete(Json.toJson(response));
        return future.thenApplyAsync(Results::ok, httpExecutionContext.current());
    }
}
