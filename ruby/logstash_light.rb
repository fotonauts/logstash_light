
require "rubygems"
require "bundler/setup"

require "redis"
require "elasticsearch"
require "json"
require "time"

def timestamp_ts(data, from, to)
  if data["@fields"] && data["@fields"][from]
    d = Time.at(data["@fields"][from] / 1000.0)
    data[to] = d.iso8601(3)
  end
end
def rename(data, from, to)
  d = data["@fields"].delete(from)
  data["@fields"][to] = d if d
end

REDIS_HOST = ENV["REDIS_HOST"]
REDIS_PORT = (ENV["REDIS_PORT"] || "6379").to_i
REDIS_QUEUE = ENV["REDIS_QUEUE"] || "prod"
ES_HOST = ENV["ES_HOST"]
# Binary transport port
ES_PORT = (ENV["ES_PORT"] || 9200).to_i


redis = Redis.new(host: REDIS_HOST, port: REDIS_PORT)
es = Elasticsearch::Client.new host: ES_HOST, port: ES_PORT

queueName = REDIS_QUEUE

puts "We have #{redis.llen(queueName)} items in the queue"

current_bulk = []
count = 0

while true do
  line = redis.lpop(queueName)
  if(line)
    data = JSON.parse(line)
    timestamp_ts(data, "date", "@timestamp")
    rename(data, "instance", "host")

    indexName = Time.now.strftime("logstash-%Y.%m.%d.%H")
    type = queueName
    current_bulk << {index: { _index: indexName, _type: type, data: data }}

    if count == 100
      puts "Sending 100 objects"
      ret = es.bulk body: current_bulk
      puts "...done"
      count = 0
      current_bulk = []
    end
    count += 1
  else
    puts "Exhausted Queue, Sleeping"
    sleep(1)
  end
end
