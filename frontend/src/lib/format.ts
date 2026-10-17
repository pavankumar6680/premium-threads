const currencyFormatters = new Map<string, Intl.NumberFormat>();

export function formatMoney(amount: number, currency = 'USD'): string {
  let formatter = currencyFormatters.get(currency);
  if (!formatter) {
    formatter = new Intl.NumberFormat('en-US', { style: 'currency', currency });
    currencyFormatters.set(currency, formatter);
  }
  return formatter.format(amount);
}

const dateFormatter = new Intl.DateTimeFormat('en-US', { dateStyle: 'medium' });
const dateTimeFormatter = new Intl.DateTimeFormat('en-US', { dateStyle: 'medium', timeStyle: 'short' });

export const formatDate = (iso: string) => dateFormatter.format(new Date(iso));
export const formatDateTime = (iso: string) => dateTimeFormatter.format(new Date(iso));

export function pluralize(count: number, singular: string, plural = `${singular}s`): string {
  return `${count} ${count === 1 ? singular : plural}`;
}
