"use client";

import Link from "next/link";
import {useRouter, useSearchParams} from "next/navigation";
import {useEffect, useState, type FormEvent} from "react";
import {apiGet} from "@/lib/api";
import {
  CATALOG_SIZE, type CatalogPage, formatCatalogCrc, parseCatalogPage,
} from "@/lib/catalog";
import {
  buildCatalogApiPath, buildCatalogHref, parseCatalogFilters,
  parseFilterOptions, type CatalogFilters, type CatalogFilterOptions,
} from "@/lib/catalog-search";
import ProductPhoto from "./ProductPhoto";
import AddToCartButton from "@/components/AddToCartButton";

type Result = {key:string; data?:CatalogPage; error?:boolean};
type FilterOptionsResult = {options?:CatalogFilterOptions; error?:boolean};

function FilterForm({
  filters, choices,
}: {
  filters: CatalogFilters;
  choices: FilterOptionsResult | null;
}) {
  const router=useRouter();
  const [q,setQ]=useState(filters.q ?? "");
  const [categoryId,setCategoryId]=useState(filters.categoryId?.toString() ?? "");
  const [brand,setBrand]=useState(filters.brand ?? "");
  const [minPrice,setMinPrice]=useState(filters.minPriceCrc ?? "");
  const [maxPrice,setMaxPrice]=useState(filters.maxPriceCrc ?? "");
  const [problem,setProblem]=useState("");

  function apply(event:FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const params = new URLSearchParams();
    if(q.trim()) params.set("q",q.trim());
    if(categoryId) params.set("categoryId",categoryId);
    if(brand.trim()) params.set("brand",brand.trim());
    if(minPrice.trim()) params.set("minPriceCrc",minPrice.trim());
    if(maxPrice.trim()) params.set("maxPriceCrc",maxPrice.trim());
    const parsed=parseCatalogFilters(params);
    if(!parsed) {
      setProblem("Comprueba la búsqueda y el rango de precios (mínimo menor o igual al máximo).");
      return;
    }
    setProblem("");
    router.push(buildCatalogHref({...parsed,page:0}));
  }

  return (
    <form className="catalog-filters" onSubmit={apply} aria-label="Buscar y filtrar productos">
      <div className="catalog-filters__row">
        <div className="catalog-filter-field">
          <label htmlFor="catalog-search">Buscar por nombre o palabra clave</label>
          <input id="catalog-search" type="search" maxLength={120}
            placeholder="Ej. mouse inalámbrico" value={q}
            onChange={e=>setQ(e.target.value)} />
        </div>
        <div className="catalog-filter-field">
          <label htmlFor="catalog-category">Categoría</label>
          <select id="catalog-category" value={categoryId}
            onChange={e=>setCategoryId(e.target.value)}>
            <option value="">Todas las categorías</option>
            {choices?.options?.categories.map(cat=>(
              <option key={cat.id} value={cat.id}>{cat.name}</option>
            ))}
          </select>
        </div>
        <div className="catalog-filter-field">
          <label htmlFor="catalog-brand">Marca</label>
          <input id="catalog-brand" type="text" list="catalog-brand-choices"
            maxLength={100} value={brand} placeholder="Cualquier marca"
            onChange={e=>setBrand(e.target.value)} />
          <datalist id="catalog-brand-choices">
            {choices?.options?.brands.map(name=><option key={name} value={name} />)}
          </datalist>
        </div>
        <div className="catalog-filter-field">
          <label htmlFor="catalog-min">Precio mínimo (₡)</label>
          <input id="catalog-min" type="number" inputMode="decimal"
            min="0" max="9999999999.99" step=".01" value={minPrice}
            onChange={e=>setMinPrice(e.target.value)} />
        </div>
        <div className="catalog-filter-field">
          <label htmlFor="catalog-max">Precio máximo (₡)</label>
          <input id="catalog-max" type="number" inputMode="decimal"
            min="0" max="9999999999.99" step=".01" value={maxPrice}
            onChange={e=>setMaxPrice(e.target.value)} />
        </div>
      </div>
      {problem && <p className="catalog-filter-error" role="alert">{problem}</p>}
      {choices?.error && <p className="catalog-filters__hint" role="status">
        No fue posible cargar las categorías y marcas sugeridas; puedes buscar por texto y precio.
      </p>}
      {!choices && <p className="catalog-filters__hint" role="status">
        Cargando opciones de categoría y marca…
      </p>}
      <div className="catalog-filters__actions">
        <button className="catalog-button" type="submit">Aplicar filtros</button>
        <Link href="/productos" className="catalog-clear-link">Limpiar filtros</Link>
      </div>
    </form>
  );
}

export default function CatalogClient() {
  const searchParams=useSearchParams();
  const filters=parseCatalogFilters(searchParams);
  const searchKey=searchParams.toString();
  const apiPath=filters ? buildCatalogApiPath(filters) : null;
  const currentPage=filters?.page ?? null;
  const [result,setResult]=useState<Result|null>(null);
  const [choices,setChoices]=useState<FilterOptionsResult|null>(null);
  const [retry,setRetry]=useState(0);

  useEffect(()=>{
    const controller=new AbortController();
    apiGet<unknown>("/api/products/filters",{signal:controller.signal,cache:"no-store"})
      .then(value=>{
        if(!controller.signal.aborted) setChoices({options:parseFilterOptions(value)});
      }).catch(()=>{
        if(!controller.signal.aborted) setChoices({error:true});
      });
    return ()=>controller.abort();
  },[]);

  useEffect(()=>{
    if(!apiPath || currentPage===null) return;
    const controller=new AbortController();
    apiGet<unknown>(apiPath,{signal:controller.signal,cache:"no-store"})
      .then(payload=>{
        const data=parseCatalogPage(payload,currentPage);
        if(!controller.signal.aborted) setResult({key:searchKey,data});
      }).catch(()=>{
        if(!controller.signal.aborted) setResult({key:searchKey,error:true});
      });
    return ()=>controller.abort();
  },[apiPath,currentPage,searchKey,retry]);

  if(!filters) return (
    <div className="catalog-feedback" role="alert">
      <h2>Filtros no válidos</h2>
      <p>Revisa la página, la categoría y el rango de precios solicitado.</p>
      <Link href="/productos">Restablecer catálogo</Link>
    </div>
  );

  const current=result?.key===searchKey?result:null;
  return (
    <>
      <FilterForm key={searchKey} filters={filters} choices={choices} />
      {!current ? <p className="catalog-feedback" role="status">
        Buscando productos en TechStore…
      </p> : current.error || !current.data ? (
        <div className="catalog-feedback" role="alert">
          <h2>No se pudo consultar el catálogo</h2>
          <p>Comprueba la API e inténtalo nuevamente; los filtros se conservan.</p>
          <button type="button" className="catalog-button"
            onClick={()=>{setResult(null);setRetry(n=>n+1);}}>Reintentar</button>
        </div>
      ) : (
        <>
          <div className="catalog-toolbar">
            <p role="status">{current.data.totalElements} producto
              {current.data.totalElements===1?"":"s"} coincidente
              {current.data.totalElements===1?"":"s"}</p>
            {current.data.totalPages>0 && <span>
              Página {filters.page+1} de {current.data.totalPages}
            </span>}
          </div>
          {current.data.items.length===0 ? (
            <div className="catalog-feedback">
              <h2>{current.data.totalElements===0
                ? "Sin resultados para los filtros" : "Página sin productos"}</h2>
              <p>{current.data.totalElements===0
                ? "Prueba otros términos, categorías o rangos de precio."
                : "No existen más productos en esa página."}</p>
              <Link href="/productos">Limpiar búsqueda</Link>
            </div>
          ) : (
            <ul className="catalog-grid">
              {current.data.items.map(product=>(
                <li key={product.id}>
                  <article className="catalog-card">
                    <ProductPhoto key={product.imageUrl??"empty"} name={product.name}
                      imageUrl={product.imageUrl}/>
                    <div className="catalog-card__body">
                      <p className="catalog-card__category">
                        {product.category?.name??"Accesorio tecnológico"}
                      </p>
                      {product.brand && <p className="catalog-card__brand">
                        Marca: {product.brand}</p>}
                      <h2><Link href={`/productos/${product.id}`}>{product.name}</Link></h2>
                      <p className="catalog-price">{formatCatalogCrc(product.priceCrc)}</p>
                      <p className={product.stock>0
                        ?"catalog-stock catalog-stock--available"
                        :"catalog-stock catalog-stock--none"}>
                        {product.stock>0
                          ? `Disponible · ${product.stock} unidad${product.stock===1?"":"es"}`
                          : "Agotado"}
                      </p>
                      <AddToCartButton productId={product.id}
                        name={product.name} stock={product.stock}/>
                      <Link className="catalog-detail-link"
                        href={`/productos/${product.id}`}>
                        Ver detalle <span aria-hidden="true">→</span>
                      </Link>
                    </div>
                  </article>
                </li>
              ))}
            </ul>
          )}
          {current.data.totalPages>1 && (
            <nav className="catalog-pagination" aria-label="Paginación del catálogo">
              {filters.page>0
                ? <Link href={buildCatalogHref({...filters,page:filters.page-1})}>
                    ← Anterior</Link>
                : <span aria-disabled="true">← Anterior</span>}
              <span aria-current="page">Página {filters.page+1}</span>
              {filters.page+1<current.data.totalPages
                ? <Link href={buildCatalogHref({...filters,page:filters.page+1})}>
                    Siguiente →</Link>
                : <span aria-disabled="true">Siguiente →</span>}
            </nav>
          )}
        </>
      )}
    </>
  );
}
