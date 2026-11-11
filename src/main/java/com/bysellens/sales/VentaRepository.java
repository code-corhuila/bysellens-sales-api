package com.bysellens.sales;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface VentaRepository extends JpaRepository<Venta, Long> {
  Optional<Venta> findByClave(String clave);

  List<Venta> findByEstadoOrderByIdDesc(String estado);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select v from Venta v where v.id=:id")
  Optional<Venta> lock(@Param("id") long id);
}
