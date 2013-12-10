
var REDIS_HOST = process.env["REDIS_HOST"];
var REDIS_PORT = parseInt(process.env["REDIS_PORT"] || "6379");
var REDIS_QUEUE = process.env["REDIS_QUEUE"] || "prod";
var ES_HOST = process.env["ES_HOST"];
// HTTP Port
var ES_PORT = parseInt(process.env["ES_PORT"] || 9200);

var redis = require('redis'),
client = redis.createClient(REDIS_PORT, REDIS_HOST);

var ElasticSearchClient = require('elasticsearchclient');
var serverOptions = {
  host: ES_HOST,
  port: ES_PORT,
};
var es = new ElasticSearchClient(serverOptions);

var strftime = require('strftime');

client.on("error", function (err) {
	console.log("error event - " + client.host + ":" + client.port + " - " + err);
});

client.llen("prod", function(err, replies) {
	console.log("Queue contains " +replies + " items");
});

var count = 0;
var es_command = [];

pro = function(err, reply) {
	if (err) {
		console.log(err);
	}
	if (reply) {
		count = count + 1;
		var data = JSON.parse(reply);
		if (data['@fields'] && data['@fields'].date) {
			var d = (new Date(data['@fields'].date)).toISOString();
			data['@timestamp'] = d;
		}
		var index = strftime('logstash-%Y.%m.%e.%H');
		var _type = REDIS_QUEUE;

		es_command.push({index: { _index: index, _type: _type} });
		es_command.push(data);

		if (count == 100) {
			console.log("Sending 100 objects");
			es.bulk(es_command).on('done', function(done){
				es_command = [];
				count = 0
				console.log("done...");
				pop();
			}).on('error', function(error){
				console.log("Something went wrong. Stopping here" + error);
			}).exec();
			client.llen("prod", function(err, replies) {
				console.log("Still "+replies+ " in queue");
			});
			return;
		}
	} else {
		console.log("Exhausted Queue, sleeping");
		setTimeout(pop, 1000);
		return;
	}
	pop();
};

pop = function() {
	client.lpop(REDIS_QUEUE, pro);
}

pop();

