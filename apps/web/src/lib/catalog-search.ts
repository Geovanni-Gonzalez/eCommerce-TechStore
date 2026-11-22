/**
 * BKL-020/021/022 — filtros públicos sincronizados con URL.
 * Solo se permiten claves y cantidades respaldadas por contrato API.
 */
import {parseCatalogPageQuery,parseProductId} from "./catalog";

export type CatalogFilters = {
  page:number;
  q:string|null;
  categoryId:number|null;
  brand:string|null;
  minPriceCrc:string|null;
  maxPriceCrc:string|null;
};
export type CatalogFilterOptions={
  categories:Array<{id:number;name:string}>;
  brands:string[];
};
type Params={get(name:string):string|null};
const parseMoney=(value:string|null):string|null|undefined=>{
  if(value===null || value==="")return null;
  if(!/^(0|[1-9][0-9]{0,9})(\.[0-9]{1,2})?$/.test(value))return undefined;
  if(Number(value)>9999999999.99)return undefined;
  return value;
};
const parseText=(text:string|null,max:number):string|null|undefined=>{
  if(text===null || !text.trim())return null;
  const clean=text.trim();
  if(clean.length>max || /[\x00-\x1f\x7f-\x9f]/.test(clean))return undefined;
  return clean;
};
export function parseCatalogFilters(params:Params):CatalogFilters|null {
  const page=parseCatalogPageQuery(params.get("page"));
  const q=parseText(params.get("q"),120);
  const brand=parseText(params.get("brand"),100);
  const rawCategory=params.get("categoryId");
  const categoryId=rawCategory===null||rawCategory===""
    ?null:parseProductId(rawCategory);
  const minPriceCrc=parseMoney(params.get("minPriceCrc"));
  const maxPriceCrc=parseMoney(params.get("maxPriceCrc"));
  if(page===null || q===undefined || brand===undefined
      || (rawCategory!==null && rawCategory!=="" && categoryId===null)
      || minPriceCrc===undefined || maxPriceCrc===undefined
      || (minPriceCrc!==null && maxPriceCrc!==null
        && Number(minPriceCrc)>Number(maxPriceCrc)))return null;
  return {page,q,brand,categoryId,minPriceCrc,maxPriceCrc};
}
function toParams(filters:CatalogFilters):URLSearchParams {
  const params=new URLSearchParams();
  if(filters.q)params.set("q",filters.q);
  if(filters.categoryId!==null)params.set("categoryId",String(filters.categoryId));
  if(filters.brand)params.set("brand",filters.brand);
  if(filters.minPriceCrc!==null)params.set("minPriceCrc",filters.minPriceCrc);
  if(filters.maxPriceCrc!==null)params.set("maxPriceCrc",filters.maxPriceCrc);
  if(filters.page>0)params.set("page",String(filters.page));
  return params;
}
export function buildCatalogHref(filters:CatalogFilters):string {
  const query=toParams(filters).toString();
  return "/productos"+(query?"?"+query:"");
}
export function buildCatalogApiPath(filters:CatalogFilters):`/${string}` {
  const params=toParams(filters);
  params.set("page",String(filters.page));
  params.set("size","12");
  return `/api/products?${params.toString()}`;
}
export function parseFilterOptions(value:unknown):CatalogFilterOptions {
  if(!value || typeof value!=="object" || Array.isArray(value))throw new Error("Opciones inválidas");
  const o=value as Record<string,unknown>;
  if(!Array.isArray(o.categories)||!Array.isArray(o.brands))throw new Error("Opciones inválidas");
  const categories=o.categories.map((raw:unknown)=>{
    if(!raw || typeof raw!=="object" || Array.isArray(raw))throw new Error("Categoría inválida");
    const cat=raw as Record<string,unknown>;
    if(typeof cat.id!=="number"||!Number.isSafeInteger(cat.id)||cat.id<1
      ||typeof cat.name!=="string"||!cat.name.trim())throw new Error("Categoría inválida");
    return {id:cat.id,name:cat.name};
  });
  const brands=o.brands.map((brand:unknown)=>{
    if(typeof brand!=="string"||!brand.trim()||brand.length>100)
      throw new Error("Marca inválida");
    return brand;
  });
  return {categories,brands};
}
