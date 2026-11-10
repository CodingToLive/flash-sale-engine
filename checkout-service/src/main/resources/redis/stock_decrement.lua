-- Atomically checks and decrements remaining stock for a SKU.
-- KEYS[1] = stock:{sku}, ARGV[1] = quantity requested.
-- Returns remaining stock after decrement, -1 if insufficient stock, -2 if the key is unset.
local stock = redis.call('GET', KEYS[1])
if stock == false then
    return -2
end
local remaining = tonumber(stock)
local requested = tonumber(ARGV[1])
if remaining < requested then
    return -1
end
redis.call('DECRBY', KEYS[1], requested)
return remaining - requested
