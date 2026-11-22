/**
 * BKL-024 — cliente administrativo. Sin almacenamiento de Bearer en navegador;
 * precios y stock se leen del servidor. Nunca se envía stock desde el CRUD.
 */
import {isSafeApiOrigin} from "./payment-result";

const BASE=(process.env.NEXT_PUBLIC_API_URL??"http://localhost:8080").replace(/\/$/,"");
export type AdminCategory={id:number;name:string};
export type AdminProduct={
  id:number;sku:string;name:string;description:string|null;
  categoryId:number|null;categoryName:string|null;brand:string|null;
  priceCrc:number;stock:number;active:boolean;
};
export type AdminProductPage={
  items:AdminProduct[];page:number;size:number;
  totalElements:number;totalPages:number;
};
export type ProductWrite={
  sku:string;name:string;description:string|null;categoryId:number|null;
  brand:string|null;priceCrc:number;
};
export class AdminCatalogError extends Error {
  constructor(public readonly status:number,message:string) {
    super(message);this.name="AdminCatalogError";
  }
}
function object(value:unknown):Record<string,unknown> {
  if(!value || typeof value!=="object" || Array.isArray(value))
    throw new Error("La API devolvió un objeto inesperado.");
  return value as Record<string,unknown>;
}
function id(v:unknown):v is number {
  return typeof v==="number" && Number.isSafeInteger(v) && v>0;
}
function safeText(v:unknown):v is string {
  return typeof v==="string" && v.trim().length>0;
}
function adminProduct(value:unknown):AdminProduct {
  const o=object(value);
  const nullableString=(v:unknown)=>v===null||v===undefined||typeof v==="string";
  if(!id(o.id)||!safeText(o.sku)||!safeText(o.name)
      ||!nullableString(o.description)||!nullableString(o.categoryName)
      ||!nullableString(o.brand)||(o.categoryId!==null && !id(o.categoryId))
      ||typeof o.priceCrc!=="number" ||!Number.isFinite(o.priceCrc)
      ||o.priceCrc<0||!Number.isSafeInteger(Math.round(o.priceCrc*100))
      ||typeof o.stock!=="number"||!Number.isSafeInteger(o.stock)||o.stock<0
      ||typeof o.active!=="boolean") {
    throw new Error("Datos de producto administrativo no válidos.");
  }
  return {
    id:o.id,sku:o.sku,name:o.name,
    description:(o.description as string|null|undefined)??null,
    categoryId:o.categoryId as number|null,
    categoryName:(o.categoryName as string|null|undefined)??null,
    brand:(o.brand as string|null|undefined)??null,
    priceCrc:o.priceCrc,stock:o.stock,active:o.active,
  };
}
function adminCategory(value:unknown):AdminCategory {
  const o=object(value);
  if(!id(o.id)||!safeText(o.name))
    throw new Error("Datos de categoría no válidos.");
  return {id:o.id,name:o.name};
}
export function parseAdminProductPage(value:unknown,page:number):AdminProductPage {
  const o=object(value);
  if(!Array.isArray(o.items)||o.page!==page||o.size!==20
      ||typeof o.totalElements!=="number"||!Number.isSafeInteger(o.totalElements)
      ||o.totalElements<0||typeof o.totalPages!=="number"
      ||!Number.isSafeInteger(o.totalPages)||o.totalPages<0
      ||o.items.length>20)throw new Error("Página administrativa no válida.");
  return {
    items:o.items.map(adminProduct),page,size:20,
    totalElements:o.totalElements,totalPages:o.totalPages,
  };
}
export function parseAdminCategories(value:unknown):AdminCategory[] {
  if(!Array.isArray(value))throw new Error("Listado de categorías no válido.");
  return value.map(adminCategory);
}
function errorMessage(status:number):string {
  switch(status) {
    case 400:return "Revisa SKU, nombre, categoría y precio. Se rechazaron los datos.";
    case 401:return "La sesión expiró. Vuelve a iniciar sesión.";
    case 403:return "Tu cuenta no tiene autorización ADMIN.";
    case 404:return "El recurso ya no existe. Actualiza el listado.";
    case 409:return "Ya existe ese SKU o nombre, la categoría no existe o aún está en uso.";
    default:return "No se pudo guardar el cambio. Comprueba la conexión y actualiza el listado.";
  }
}
async function request(token:string,method:"GET"|"POST"|"PUT"|"PATCH"|"DELETE",
  path:string,body?:unknown,signal?:AbortSignal):Promise<unknown|null> {
  if(!isSafeApiOrigin(BASE)|| !/^[A-Za-z0-9_-]{32,}$/.test(token))
    throw new Error("Se requiere una conexión segura y sesión ADMIN.");
  const response=await fetch(BASE+path,{
    method,signal,cache:"no-store",credentials:"omit",referrerPolicy:"no-referrer",
    headers:{Authorization:"Bearer "+token,Accept:"application/json",
      ...(body!==undefined?{"Content-Type":"application/json"}:{})},
    ...(body!==undefined?{body:JSON.stringify(body)}:{}),
  });
  if(!response.ok)throw new AdminCatalogError(response.status,errorMessage(response.status));
  return response.status===204?null:(await response.json()) as unknown;
}
export async function listAdminProducts(token:string,page:number,signal?:AbortSignal) {
  const result=await request(token,"GET",
    "/api/admin/catalog/products?page="+page+"&size=20",undefined,signal);
  return parseAdminProductPage(result,page);
}
export async function listAdminCategories(token:string,signal?:AbortSignal) {
  return parseAdminCategories(await request(token,"GET","/api/admin/catalog/categories",
    undefined,signal));
}
export async function saveAdminProduct(token:string,product:ProductWrite,id?:number) {
  return adminProduct(await request(token,id?"PUT":"POST",
    "/api/admin/catalog/products"+(id?"/"+id:""),product));
}
export async function setAdminProductActive(token:string,id:number,active:boolean) {
  return adminProduct(await request(token,"PATCH",
    "/api/admin/catalog/products/"+id+"/status",{active}));
}
export async function createAdminCategory(token:string,name:string) {
  return adminCategory(await request(token,"POST",
    "/api/admin/catalog/categories",{name}));
}
export async function renameAdminCategory(token:string,id:number,name:string) {
  return adminCategory(await request(token,"PUT",
    "/api/admin/catalog/categories/"+id,{name}));
}
export async function deleteAdminCategory(token:string,id:number) {
  await request(token,"DELETE","/api/admin/catalog/categories/"+id);
}
