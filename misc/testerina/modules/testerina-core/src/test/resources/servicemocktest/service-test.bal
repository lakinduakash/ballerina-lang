package src.test.resources.servicemocktest;

import ballerina.net.http;
import ballerina.io;
import ballerina.test;
import ballerina.config;

endpoint http:ServiceEndpoint eventEP {
    port: 9092
};

string url1 = "http://0.0.0.0:9092/events";
string url2 = "http://0.0.0.0:9090/portal";

@http:ServiceConfig {
    basePath: "/events"
}
service<http:Service> EventServiceMock bind eventEP {

    @http:ResourceConfig {
        methods:["GET"],
        path:"/"
    }
    getEvents (endpoint client, http:Request req) {
        http:Response res = {};
        json j = {"a":"b"};
        res.setJsonPayload(j);
        _ = client -> respond(res);
    }
}

function init() {
    // TODO: url1 and url2 should be assigned to following. it's not working right now.
    string x = test:startService("EventServiceMock");
    // currently the url is hard coded; need to set url1 in to config api here
    string y = test:startService("PortalService");
    io:println(url2);
}

@test:config{before: "init"}
function testService () {
    endpoint http:ClientEndpoint httpEndpoint {
        targets:[{
            uri:url2
        }]
    };
    http:Request req = {};
    // Send a GET request to the specified endpoint
    http:Response resp = {};
    http:HttpConnectorError err;
    resp, err = httpEndpoint -> get("/events", req);
    if (err != null) {
        test:assertFail(msg = "Failed to call the endpoint: "+url2);
    }
    io:println("GET request:");
    var jsonRes, _ = resp.getJsonPayload();
    json expected = {"a":"b"};
    test:assertEquals(jsonRes, expected);

    test:stopService("EventServiceMock");
    test:stopService("PortalService");
}