#!/usr/bin/env bash
set -euo pipefail

CONFIG_FILE="${1:-service/src/main/resources/localconfig.yaml}"

if [[ ! -f "$CONFIG_FILE" ]]; then
  echo "Gemini check failed: local configuration was not found."
  exit 1
fi

API_KEY="$(ruby -ryaml -e 'config = YAML.load_file(ARGV.fetch(0)) || {}; print config.dig("gemini", "apiKey").to_s.strip' "$CONFIG_FILE")"
if [[ -z "$API_KEY" ]]; then
  echo "Gemini check failed: gemini.apiKey is not configured."
  exit 1
fi

RESPONSE_FILE="$(mktemp)"
MODEL_FILE="$(mktemp)"
trap 'rm -f "$RESPONSE_FILE" "$MODEL_FILE"' EXIT

MODEL_STATUS_CODE="$(curl --silent --show-error --max-time 30 --output "$MODEL_FILE" --write-out '%{http_code}' \
  --header "x-goog-api-key: $API_KEY" \
  'https://generativelanguage.googleapis.com/v1beta/models')"

if [[ "$MODEL_STATUS_CODE" != "200" ]]; then
  echo "Gemini check failed while authenticating the API key (HTTP $MODEL_STATUS_CODE)."
  exit 1
fi

if ! ruby -rjson -e 'models = JSON.parse(File.read(ARGV.fetch(0))).fetch("models", []); supported = models.any? { |model| model["name"] == "models/gemini-3.8-flash" && model.fetch("supportedGenerationMethods", []).include?("generateContent") }; exit(supported ? 0 : 1)' "$MODEL_FILE"; then
  echo "Gemini check failed: gemini-3.8-flash is unavailable to this API key."
  exit 1
fi

STATUS_CODE="$(curl --silent --show-error --max-time 60 --output "$RESPONSE_FILE" --write-out '%{http_code}' \
  --request POST 'https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent' \
  --header "x-goog-api-key: $API_KEY" \
  --header 'Content-Type: application/json' \
  --data '{"contents":[{"parts":[{"text":"Reply with the single word: ready"}]}]}')"

case "$STATUS_CODE" in
  200) ;;
  429|503)
    echo "Gemini API key is valid, but the provider is temporarily unavailable (HTTP $STATUS_CODE)."
    exit 2
    ;;
  *)
    echo "Gemini check failed with HTTP $STATUS_CODE."
    exit 1
    ;;
esac

if ruby -rjson -e 'body = JSON.parse(File.read(ARGV.fetch(0))); exit(body.dig("candidates", 0, "content", "parts", 0, "text").to_s.empty? ? 1 : 0)' "$RESPONSE_FILE"; then
  echo "Gemini API key check passed."
else
  echo "Gemini check failed: the API returned no generated text."
  exit 1
fi
