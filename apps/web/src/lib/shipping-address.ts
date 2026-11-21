/**
 * BKL-031: dirección exacta de ShippingAddress Java.
 * Se enviará a POST /api/orders solo cuando exista el checkout visual BKL-034.
 */
export const COSTA_RICA_PROVINCES = [
  "San José", "Alajuela", "Cartago", "Heredia",
  "Guanacaste", "Puntarenas", "Limón",
] as const;
export const SHIPPING_FIELDS = [
  "province", "canton", "district", "details", "recipientName", "phone",
] as const;
export type ShippingField = typeof SHIPPING_FIELDS[number];
export type ShippingAddress = Record<ShippingField, string>;
export type ShippingErrors = Partial<Record<ShippingField, string>>;
export const EMPTY_SHIPPING_ADDRESS: ShippingAddress = {
  province: "", canton: "", district: "", details: "",
  recipientName: "", phone: "",
};
const limits: Record<ShippingField, [number,number]> = {
  province:[1,80],canton:[1,80],district:[1,80],
  details:[5,300],recipientName:[2,120],phone:[8,24],
};
const labels:Record<ShippingField,string> = {
  province:"La provincia",canton:"El cantón",district:"El distrito",
  details:"Las señas",recipientName:"El nombre de quien recibe",
  phone:"El teléfono de contacto",
};
const controls = /[\u0000-\u001f\u007f-\u009f]/u;
export function normalizeShippingAddress(value: ShippingAddress): ShippingAddress {
  return {
    province:value.province.trim(), canton:value.canton.trim(),
    district:value.district.trim(), details:value.details.trim(),
    recipientName:value.recipientName.trim(), phone:value.phone.trim(),
  };
}
export function validateShippingAddress(address: ShippingAddress): ShippingErrors {
  const errors:ShippingErrors = {};
  for (const field of SHIPPING_FIELDS) {
    const text = address[field].trim();
    const [min,max] = limits[field];
    if (text.length < min || text.length > max || controls.test(text)) {
      errors[field] = labels[field] + " debe contener entre " + min
        + " y " + max + " caracteres válidos.";
    }
  }
  if (address.province.trim()
      && !COSTA_RICA_PROVINCES.some(p => p === address.province.trim())) {
    errors.province = "Selecciona una provincia de Costa Rica.";
  }
  return errors;
}
export function makeShippingAddressBody(address: ShippingAddress) {
  const normalized = normalizeShippingAddress(address);
  if (Object.keys(validateShippingAddress(normalized)).length > 0) {
    throw new Error("Completa una dirección de entrega válida.");
  }
  return {shippingAddress:normalized};
}
