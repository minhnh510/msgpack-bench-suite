-- Lua transform for benchmark
-- Returns score and flag derived from row fields
function transform_row(id, price, qty, vol)
  local base = price * qty
  local score = (base * 0.013) + (vol * 0.21) + (id % 7) * 0.5
  local flag = ((id % 3) ~= 0)
  return score, flag
end
