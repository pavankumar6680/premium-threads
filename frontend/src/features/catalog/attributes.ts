import type { AttributeValue } from './types';

export function formatAttributeValue(type: AttributeValue['type'], unit: string | null, value: string): string {
  if (type === 'BOOL') return value === 'true' ? 'Yes' : 'No';
  return unit ? `${value} ${unit}` : value;
}

export function formatAttribute(attribute: AttributeValue): string {
  return attribute.values.map((v) => formatAttributeValue(attribute.type, attribute.unit, v)).join(', ');
}
