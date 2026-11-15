/**
 * Form-level schemas (what the inputs hold) and converters to/from the stored node config. Converters omit empty
 * optional fields so the saved config stays minimal and schema-valid. Import from "@/schemas/node-forms".
 */
export * from "./basics";
export * from "./condition-form";
export * from "./delay-form";
export { optionalNumber } from "./form-helpers";
export * from "./http-form";
export { formatJson, formatLiteral, jsonAnyText, jsonObjectText, parseLiteral } from "./literals";
export * from "./message-forms";
export * from "./transform-form";
export * from "./trigger-forms";
export * from "./workflow-forms";
