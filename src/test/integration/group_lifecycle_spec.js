require('./integration_config.js');

var request = require('request');
var http = require('http');
var ip = require('ip');
var channelName = utils.randomChannelName();
var groupName = utils.randomChannelName();
var channelResource = channelUrl + "/" + channelName;
var testName = __filename;
var groupConfig = {
    callbackUrl : 'http://' + ip.address() + ':8888/',
    channelUrl: channelResource,
    transactional: true
};

/**
 * This should:
 *
 * 1 - create a channel
 * 2 - create a group on that channel
 * 3 - start a server at the endpoint
 * 4 - post items into the channel
 * 5 - verify that the records are returned within delta time
 */
describe(testName, function () {
    console.log('using ' + groupConfig.callbackUrl);
    utils.createChannel(channelName);

    utils.putGroup(groupName, groupConfig);

    function postItem() {
        request.post({url : channelResource,
                headers : {"Content-Type" : "application/json", user : 'somebody' },
                body : JSON.stringify({ "data" : Date.now()})},
            function (err, response, body) {
                expect(err).toBeNull();
                expect(response.statusCode).toBe(201);
            });
    }

    it('runs callback server', function () {
        var items = [];
        var started = false;
        var server;
        var closed = false;

        runs(function () {
            server = http.createServer(function (request, response) {
                request.on('data', function(chunk) {
                    items.push(chunk.toString());
                });
                response.writeHead(200);
                response.end();
            });

            server.on('connection', function(socket) {
                socket.setTimeout(1000);
            });

            server.listen(8888, function () {
                started = true;
            });
        });

        waitsFor(function() {
            return started;
        }, 11000);

        runs(function () {
            postItem();
            postItem();
            postItem();
            postItem();
        });

        waitsFor(function() {
            return items.length == 4;
        }, 12000);

        runs(function () {
            server.close(function () {
                closed = true;
            });

            for (var i = 0; i < items.length; i++) {
                var parse = JSON.parse(items[i]);
                expect(parse.uris[0]).toBe(channelResource + '/100' + i);
            }
        });

        waitsFor(function() {
            return closed;
        }, 9000);

    });

});
