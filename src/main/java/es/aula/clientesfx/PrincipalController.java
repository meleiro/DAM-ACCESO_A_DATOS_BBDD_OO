package es.aula.clientesfx;

// Clases necesarias para trabajar con JDBC.
import java.sql.*;

// Colecciones y tratamiento de valores opcionales.
import java.util.List;
import java.util.Optional;

// Clases para ejecutar operaciones en segundo plano.
import java.util.concurrent.*;
import java.util.function.Consumer;

// Componentes y utilidades de JavaFX.
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;

/**
 * Controlador principal de la aplicación JavaFX.
 *
 * Se encarga de:
 *
 * - Gestionar los componentes gráficos definidos en el FXML.
 * - Recoger y validar los datos introducidos por el usuario.
 * - Invocar los métodos de acceso a la base de datos.
 * - Mostrar los resultados de las consultas.
 * - Gestionar los errores y las ventanas de diálogo.
 *
 * La aplicación dispone de dos zonas de trabajo:
 *
 * 1. Repaso del modelo relacional tradicional.
 * 2. Trabajo con el modelo objeto-relacional de PostgreSQL.
 *
 * Las operaciones de acceso a datos se delegan en los DAO.
 */
public final class PrincipalController {

    // =====================================================
    // COMPONENTES GENERALES DE LA INTERFAZ
    // =====================================================

    // @FXML permite vincular un atributo o método Java
    // con un elemento definido en el archivo FXML.

    // Contenedor principal de la zona de trabajo.
    @FXML
    private BorderPane zonaTrabajo;

    // Etiquetas para mostrar el estado de las operaciones
    // y el número total de clientes.
    @FXML
    private Label lblEstado, lblTotal;


    // =====================================================
    // COMPONENTES DEL MODELO RELACIONAL
    // =====================================================

    // Tabla que muestra objetos de tipo ClienteBasico.
    @FXML
    private TableView<ClienteBasico> tablaRepaso;

    // Columnas de la tabla de clientes básicos.
    // El primer tipo indica el objeto representado.
    // El segundo indica el tipo de dato mostrado.
    @FXML
    private TableColumn<ClienteBasico, String>
            rId, rNombre, rEmail, rCiudad;

    // Campos de texto del formulario de clientes básicos.
    @FXML
    private TextField
            txtRNombre, txtREmail, txtRCiudad, txtRFiltro;


    // =====================================================
    // COMPONENTES DEL MODELO OBJETO-RELACIONAL
    // =====================================================

    // Tabla que muestra objetos de tipo Cliente.
    @FXML
    private TableView<Cliente> tablaObjetos;

    // Columnas que representan tanto atributos simples
    // como estructuras de datos más complejas.
    @FXML
    private TableColumn<Cliente, String>
            oId, oNombre, oEmail, oDireccion, oTelefonos, oJson;

    // Campos de texto del formulario objeto-relacional.
    @FXML
    private TextField
            txtONombre, txtOEmail, txtOCalle, txtOCiudad,
            txtOCp, txtOTelefonos, txtOFiltro;

    // Área de texto que permite introducir documentos JSON.
    @FXML
    private TextArea txtOJson;


    // =====================================================
    // OBJETOS DE ACCESO A DATOS
    // =====================================================

    // DAO encargado de gestionar los clientes básicos
    // mediante el modelo relacional tradicional.
    private final ClienteBasicoDao repaso =
            new ClienteBasicoDao();

    // Utilizamos una interfaz como tipo de la variable.
    // ClienteDao proporciona la implementación concreta
    // de los métodos definidos en ClienteRepository.
    private final ClienteRepository objetos =
            new ClienteDao();


    // =====================================================
    // GESTIÓN DE HILOS
    // =====================================================

    /**
     * ExecutorService permite ejecutar operaciones
     * en un hilo diferente al hilo gráfico de JavaFX.
     *
     * Esto evita que la interfaz quede bloqueada mientras
     * esperamos una respuesta de la base de datos.
     *
     * Utilizamos un único hilo para que las operaciones
     * JDBC se ejecuten de forma secuencial.
     */
    private final ExecutorService executor =
            Executors.newSingleThreadExecutor(r -> {

                // Creamos un hilo con un nombre identificativo.
                Thread hilo = new Thread(r, "jdbc-aula");

                // Un hilo daemon no impide que finalice
                // la máquina virtual cuando se cierra la aplicación.
                hilo.setDaemon(true);

                return hilo;
            });

    // Indica si el controlador se ha cerrado.
    private boolean cerrado;


    // =====================================================
    // INICIALIZACIÓN DE LA INTERFAZ
    // =====================================================

    /**
     * JavaFX ejecuta automáticamente este método después
     * de cargar el archivo FXML e inyectar sus componentes.
     *
     * Aquí configuramos las tablas y los eventos asociados
     * a la selección de clientes.
     */
    @FXML
    private void initialize() {

        // -------------------------------------------------
        // CONFIGURACIÓN DE LA TABLA RELACIONAL
        // -------------------------------------------------

        // setCellValueFactory indica cómo obtener el valor
        // que debe mostrarse en cada celda de una columna.

        // ClienteBasico es un record, por lo que sus métodos
        // de acceso son id(), nombre(), email(), etc.
        // No utiliza los getters tradicionales getId().

        rId.setCellValueFactory(
                c -> valor(c.getValue().id())
        );

        rNombre.setCellValueFactory(
                c -> valor(c.getValue().nombre())
        );

        rEmail.setCellValueFactory(
                c -> valor(c.getValue().email())
        );

        rCiudad.setCellValueFactory(
                c -> valor(c.getValue().ciudad())
        );


        // -------------------------------------------------
        // CONFIGURACIÓN DE LA TABLA OBJETO-RELACIONAL
        // -------------------------------------------------

        // Mostramos los atributos simples del cliente.

        oId.setCellValueFactory(
                c -> valor(c.getValue().id())
        );

        oNombre.setCellValueFactory(
                c -> valor(c.getValue().nombre())
        );

        oEmail.setCellValueFactory(
                c -> valor(c.getValue().email())
        );

        // La dirección es un objeto compuesto.
        // Accedemos a sus atributos individuales y los
        // concatenamos para mostrarlos en una única columna.

        oDireccion.setCellValueFactory(c -> valor(
                c.getValue().direccion().calle() + ", " +
                        c.getValue().direccion().ciudad() + " (" +
                        c.getValue().direccion().codigoPostal() + ")"
        ));

        // Los teléfonos se representan mediante una lista.
        // String.join permite convertirlos en una cadena
        // separando cada teléfono mediante punto y coma.

        oTelefonos.setCellValueFactory(
                c -> valor(
                        String.join("; ", c.getValue().telefonos())
                )
        );

        // Mostramos las preferencias JSON como texto.

        oJson.setCellValueFactory(
                c -> valor(c.getValue().preferenciasJson())
        );


        // -------------------------------------------------
        // MENSAJES DE LAS TABLAS VACÍAS
        // -------------------------------------------------

        // setPlaceholder permite indicar qué componente
        // se muestra cuando una tabla no contiene registros.

        tablaRepaso.setPlaceholder(
                new Label("Pulsa Cargar todos para consultar el repaso.")
        );

        tablaObjetos.setPlaceholder(
                new Label("Completa listar() y pulsa Cargar todos.")
        );


        // -------------------------------------------------
        // EVENTO DE SELECCIÓN: TABLA RELACIONAL
        // -------------------------------------------------

        // Añadimos un listener que se ejecutará cada vez
        // que cambie el cliente seleccionado en la tabla.
        //
        // obs: propiedad observada.
        // antes: cliente seleccionado anteriormente.
        // c: nuevo cliente seleccionado.

        tablaRepaso.getSelectionModel()
                .selectedItemProperty()
                .addListener((obs, antes, c) -> {

                    // Comprobamos que exista una selección.
                    if (c != null) {

                        // Trasladamos los datos del cliente
                        // seleccionado a los campos del formulario.

                        txtRNombre.setText(c.nombre());
                        txtREmail.setText(c.email());
                        txtRCiudad.setText(c.ciudad());
                    }
                });


        // -------------------------------------------------
        // EVENTO DE SELECCIÓN: TABLA OBJETO-RELACIONAL
        // -------------------------------------------------

        tablaObjetos.getSelectionModel()
                .selectedItemProperty()
                .addListener((obs, antes, c) -> {

                    if (c != null) {

                        // Cargamos los atributos simples.
                        txtONombre.setText(c.nombre());
                        txtOEmail.setText(c.email());

                        // Descomponemos el objeto Direccion
                        // en sus diferentes atributos.
                        txtOCalle.setText(c.direccion().calle());
                        txtOCiudad.setText(c.direccion().ciudad());
                        txtOCp.setText(c.direccion().codigoPostal());

                        // Convertimos la lista de teléfonos
                        // en una cadena separada por punto y coma.
                        txtOTelefonos.setText(
                                String.join(";", c.telefonos())
                        );

                        // Cargamos el documento JSON.
                        txtOJson.setText(c.preferenciasJson());
                    }
                });

        // No realizamos ninguna conexión automáticamente.
        // Esto permite abrir la interfaz aunque todavía
        // no se haya configurado la base de datos.

        lblEstado.setText(
                "Interfaz preparada. Monta la base y pulsa Comprobar conexión."
        );
    }


    /**
     * Convierte cualquier objeto en un valor de texto
     * observable por los componentes de JavaFX.
     *
     * TableColumn necesita que su CellValueFactory
     * devuelva un ObservableValue.
     *
     * ReadOnlyStringWrapper proporciona ese comportamiento.
     *
     * @param s Objeto que queremos representar.
     * @return Valor de texto compatible con JavaFX.
     */
    private static ReadOnlyStringWrapper valor(Object s) {

        return new ReadOnlyStringWrapper(
                String.valueOf(s)
        );
    }


    // =====================================================
    // EJECUCIÓN ASÍNCRONA DE OPERACIONES JDBC
    // =====================================================

    /**
     * Método genérico que centraliza la ejecución de
     * operaciones de acceso a la base de datos.
     *
     * Permite reutilizar la misma lógica para insertar,
     * consultar, actualizar y eliminar clientes.
     *
     * @param mensaje Mensaje mostrado durante la operación.
     * @param operacion Operación que se ejecutará en segundo plano.
     * @param alTerminar Acción que recibe el resultado.
     * @param <T> Tipo de dato devuelto por la operación.
     */
    private <T> void ejecutar(
            String mensaje,
            Callable<T> operacion,
            Consumer<T> alTerminar
    ) {

        // Si la aplicación se está cerrando,
        // no iniciamos nuevas operaciones.
        if (cerrado) {
            return;
        }

        // Deshabilitamos temporalmente la zona de trabajo
        // para evitar nuevas interacciones del usuario.
        zonaTrabajo.setDisable(true);

        // Informamos de la operación en curso.
        lblEstado.setText(mensaje);

        // Task representa una tarea que puede ejecutarse
        // en segundo plano y notificar su resultado.
        Task<T> tarea = new Task<>() {

            /**
             * Este método se ejecutará en el hilo JDBC,
             * no en el hilo gráfico de JavaFX.
             */
            @Override
            protected T call() throws Exception {

                // Ejecutamos la operación recibida
                // mediante el objeto Callable.
                return operacion.call();
            }
        };

        // Evento ejecutado cuando la tarea termina
        // correctamente y devuelve un resultado.
        //
        // Este evento se ejecuta en el hilo de JavaFX,
        // por lo que podemos modificar la interfaz.

        tarea.setOnSucceeded(e -> {

            if (cerrado) {
                return;
            }

            // Volvemos a habilitar los controles.
            zonaTrabajo.setDisable(false);

            lblEstado.setText("Operación completada.");

            // getValue() recupera el resultado de call().
            // accept() entrega ese resultado al Consumer.
            alTerminar.accept(tarea.getValue());
        });

        // Evento ejecutado cuando se produce una excepción
        // durante la ejecución de la tarea.

        tarea.setOnFailed(e -> {

            if (cerrado) {
                return;
            }

            zonaTrabajo.setDisable(false);

            // Recuperamos la excepción y la enviamos
            // al método encargado de gestionar errores.
            error(tarea.getException());
        });

        // Enviamos la tarea al executor para que
        // se ejecute en el hilo de trabajo.
        executor.execute(tarea);
    }


    // =====================================================
    // COMPROBACIÓN DE LA CONEXIÓN
    // =====================================================

    /**
     * Comprueba si es posible establecer una conexión
     * con el servidor de base de datos.
     */
    @FXML
    private void comprobarConexion() {

        ejecutar("Comprobando conexión…", () -> {

            // Try-with-resources garantiza que la conexión
            // se cierre automáticamente al finalizar.
            try (Connection c = Conexion.abrir()) {

                // Consultamos los metadatos de la conexión
                // para conocer el gestor y la base de datos.

                return c.getMetaData().getDatabaseProductName()
                        + " · " + c.getCatalog();
            }

        }, info -> lblEstado.setText(
                "Conexión correcta: " + info
        ));
    }


    // =====================================================
    // MODELO RELACIONAL: CONSULTAS
    // =====================================================

    /**
     * Carga todos los clientes básicos.
     */
    @FXML
    private void cargarRepaso() {

        // Eliminamos cualquier filtro anterior.
        txtRFiltro.clear();

        // null indica que no aplicamos filtro por ciudad.
        listarRepaso(null);
    }


    /**
     * Consulta los clientes aplicando un filtro por ciudad.
     */
    @FXML
    private void filtrarRepaso() {

        // trim() elimina los espacios al principio y al final.
        listarRepaso(txtRFiltro.getText().trim());
    }


    /**
     * Recupera los clientes básicos desde el DAO.
     *
     * @param ciudad Ciudad por la que queremos filtrar.
     */
    private void listarRepaso(String ciudad) {

        ejecutar(
                "Cargando repaso…",

                // Operación ejecutada en segundo plano.
                () -> repaso.listar(ciudad),

                // Acción ejecutada al recibir la lista.
                lista -> {

                    // Sustituimos los elementos de la tabla
                    // por los recuperados de la base de datos.
                    tablaRepaso.getItems().setAll(lista);

                    // Limpiamos el formulario.
                    limpiarRepaso();

                    // Mostramos el número de registros visibles.
                    lblEstado.setText(
                            "Repaso: " + lista.size()
                                    + " clientes visibles."
                    );
                }
        );
    }


    // =====================================================
    // MODELO RELACIONAL: INSERTAR
    // =====================================================

    /**
     * Recoge los datos del formulario, los valida
     * e inserta un nuevo cliente básico.
     */
    @FXML
    private void insertarRepaso() {

        try {

            // Leemos y validamos los controles gráficos
            // antes de iniciar el hilo JDBC.
            //
            // El identificador 0 representa un cliente nuevo.
            // El DAO se encarga de realizar la inserción.

            ClienteBasico c = new ClienteBasico(
                    0,
                    Validacion.obligatorio(
                            txtRNombre.getText(), "Nombre"
                    ),
                    Validacion.email(txtREmail.getText()),
                    Validacion.obligatorio(
                            txtRCiudad.getText(), "Ciudad"
                    )
            );

            ejecutar("Insertando cliente de repaso…", () -> {

                // Insertamos el cliente y recuperamos su ID.
                long id = repaso.insertar(c);

                // Devolvemos un objeto Cambio que contiene
                // un mensaje y la lista actualizada de clientes.
                return new Cambio<>(
                        "Cliente creado con id=" + id,
                        repaso.listar(null)
                );

            }, this::mostrarCambioRepaso);

        } catch (IllegalArgumentException e) {

            // Si los datos no superan la validación,
            // mostramos un aviso al usuario.
            aviso(e.getMessage());
        }
    }


    // =====================================================
    // MODELO RELACIONAL: ACTUALIZAR
    // =====================================================

    /**
     * Actualiza el cliente seleccionado en la tabla.
     */
    @FXML
    private void actualizarRepaso() {

        // Recuperamos el cliente seleccionado.
        ClienteBasico elegido =
                tablaRepaso.getSelectionModel().getSelectedItem();

        // No podemos actualizar si no existe una selección.
        if (elegido == null) {

            aviso("Selecciona un cliente de repaso.");
            return;
        }

        try {

            // Creamos un nuevo record con el mismo ID
            // y los nuevos datos del formulario.
            //
            // Los records no permiten reasignar directamente
            // sus componentes una vez construidos.

            ClienteBasico c = new ClienteBasico(
                    elegido.id(),
                    Validacion.obligatorio(
                            txtRNombre.getText(), "Nombre"
                    ),
                    Validacion.email(txtREmail.getText()),
                    Validacion.obligatorio(
                            txtRCiudad.getText(), "Ciudad"
                    )
            );

            ejecutar("Actualizando cliente de repaso…", () -> {

                // El DAO devuelve true si se ha actualizado
                // el cliente y false si no se ha encontrado.
                boolean ok = repaso.actualizar(c);

                return new Cambio<>(
                        ok
                                ? "Cliente actualizado."
                                : "El cliente ya no existe.",
                        repaso.listar(null)
                );

            }, this::mostrarCambioRepaso);

        } catch (IllegalArgumentException e) {

            aviso(e.getMessage());
        }
    }


    // =====================================================
    // MODELO RELACIONAL: ELIMINAR
    // =====================================================

    /**
     * Elimina el cliente seleccionado previa confirmación.
     */
    @FXML
    private void eliminarRepaso() {

        ClienteBasico c =
                tablaRepaso.getSelectionModel().getSelectedItem();

        // Comprobamos que haya un cliente seleccionado.
        if (c == null) {

            aviso("Selecciona un cliente de repaso.");
            return;
        }

        // Solicitamos confirmación antes de eliminar.
        // Si el usuario cancela, finalizamos el método.
        if (!confirmar("Eliminar a " + c.nombre() + " del repaso")) {
            return;
        }

        ejecutar("Eliminando cliente de repaso…", () -> {

            // Eliminamos utilizando el identificador.
            boolean ok = repaso.eliminar(c.id());

            // Recuperamos los datos actualizados.
            return new Cambio<>(
                    ok
                            ? "Cliente eliminado."
                            : "El cliente ya no existe.",
                    repaso.listar(null)
            );

        }, this::mostrarCambioRepaso);
    }


    /**
     * Actualiza la interfaz después de una operación
     * de inserción, actualización o eliminación.
     */
    private void mostrarCambioRepaso(
            Cambio<ClienteBasico> cambio
    ) {

        // Mostramos la nueva lista de clientes.
        tablaRepaso.getItems().setAll(cambio.filas());

        // Eliminamos el filtro y limpiamos el formulario.
        txtRFiltro.clear();
        limpiarRepaso();

        // Mostramos el resultado de la operación.
        lblEstado.setText(cambio.mensaje());
    }


    /**
     * Limpia los campos del formulario relacional.
     */
    @FXML
    private void limpiarRepaso() {

        // Eliminamos la selección actual de la tabla.
        tablaRepaso.getSelectionModel().clearSelection();

        // Vaciamos los campos de texto.
        txtRNombre.clear();
        txtREmail.clear();
        txtRCiudad.clear();
    }


    // =====================================================
    // MODELO OBJETO-RELACIONAL: CONSULTAS
    // =====================================================

    /**
     * Carga todos los clientes objeto-relacionales.
     */
    @FXML
    private void cargarObjetos() {

        txtOFiltro.clear();
        listarObjetos(null);
    }


    /**
     * Filtra los clientes objeto-relacionales por ciudad.
     */
    @FXML
    private void filtrarObjetos() {

        String s = txtOFiltro.getText().trim();

        // isBlank() comprueba si la cadena está vacía
        // o contiene únicamente espacios en blanco.
        //
        // Si está vacía, enviamos null para no filtrar.
        listarObjetos(s.isBlank() ? null : s);
    }


    /**
     * Consulta los clientes utilizando el repositorio
     * objeto-relacional.
     */
    private void listarObjetos(String ciudad) {

        ejecutar(
                "Cargando clientes objeto-relacionales…",

                // El repositorio realiza la consulta JDBC
                // y transforma los registros en objetos Cliente.
                () -> objetos.listar(ciudad),

                lista -> {

                    tablaObjetos.getItems().setAll(lista);

                    limpiarObjetos();

                    lblEstado.setText(
                            "Objeto-relacional: " + lista.size()
                                    + " clientes visibles."
                    );
                }
        );
    }


    // =====================================================
    // MODELO OBJETO-RELACIONAL: INSERTAR
    // =====================================================

    /**
     * Inserta un cliente con atributos simples
     * y estructuras de datos complejas.
     *
     * El cliente contiene:
     *
     * - Identificador.
     * - Nombre.
     * - Email.
     * - Objeto Direccion.
     * - Lista de teléfonos.
     * - Documento JSON de preferencias.
     */
    @FXML
    private void insertarObjetos() {

        try {

            Cliente cliente = new Cliente(

                    // Identificador inicial.
                    0,

                    // Validación de atributos simples.
                    Validacion.obligatorio(
                            txtONombre.getText(), "Nombre"
                    ),

                    Validacion.email(txtOEmail.getText()),

                    // Construimos un objeto Direccion.
                    // Este objeto representa en Java
                    // el tipo compuesto de PostgreSQL.

                    new Direccion(

                            Validacion.obligatorio(
                                    txtOCalle.getText(), "Calle"
                            ),

                            Validacion.obligatorio(
                                    txtOCiudad.getText(), "Ciudad"
                            ),

                            Validacion.cp(txtOCp.getText())
                    ),

                    // Convertimos y validamos los teléfonos
                    // introducidos en el formulario.
                    Validacion.telefonos(
                            txtOTelefonos.getText()
                    ),

                    // Recogemos el documento JSON como texto.
                    // El DAO será el encargado de enviarlo
                    // a la columna JSONB de PostgreSQL.
                    Validacion.obligatorio(
                            txtOJson.getText(), "Preferencias JSON"
                    )
            );

            ejecutar("Insertando cliente objeto-relacional…", () -> {

                // Delegamos la inserción en el repositorio.
                long id = objetos.insertar(cliente);

                // Recuperamos el listado actualizado.
                return new Cambio<>(
                        "Cliente creado con id=" + id,
                        objetos.listar(null)
                );

            }, this::mostrarCambioObjetos);

        } catch (IllegalArgumentException e) {

            aviso(e.getMessage());
        }
    }


    // =====================================================
    // MODELO OBJETO-RELACIONAL: ACTUALIZAR CONTACTO
    // =====================================================

    /**
     * Actualiza únicamente el email y los teléfonos
     * del cliente seleccionado.
     */
    @FXML
    private void actualizarContacto() {

        // Recuperamos el cliente seleccionado.
        Cliente c = seleccionado();

        if (c == null) {
            return;
        }

        try {

            // Validamos el nuevo email.
            String email = Validacion.email(
                    txtOEmail.getText()
            );

            // Obtenemos la lista de teléfonos.
            // El DAO deberá gestionar su correspondencia
            // con el array almacenado en PostgreSQL.
            List<String> telefonos = Validacion.telefonos(
                    txtOTelefonos.getText()
            );

            ejecutar("Actualizando contacto…", () -> {

                // Actualizamos únicamente los datos
                // de contacto del cliente.
                boolean ok = objetos.actualizarContacto(
                        c.id(),
                        email,
                        telefonos
                );

                return new Cambio<>(
                        ok
                                ? "Email y teléfonos actualizados."
                                : "El cliente ya no existe.",
                        objetos.listar(null)
                );

            }, this::mostrarCambioObjetos);

        } catch (IllegalArgumentException e) {

            aviso(e.getMessage());
        }
    }


    // =====================================================
    // PROCEDIMIENTO ALMACENADO: CAMBIAR CIUDAD
    // =====================================================

    /**
     * Modifica la ciudad del cliente seleccionado
     * mediante un procedimiento almacenado en PostgreSQL.
     *
     * El procedimiento recibe:
     *
     * - Identificador del cliente.
     * - Nueva ciudad.
     *
     * La llamada JDBC se realiza desde ClienteDao.
     */
    @FXML
    private void cambiarCiudad() {

        Cliente c = seleccionado();

        if (c == null) {
            return;
        }

        try {

            // Validamos que la nueva ciudad no esté vacía.
            String ciudad = Validacion.obligatorio(
                    txtOCiudad.getText(),
                    "Ciudad nueva"
            );

            ejecutar("Ejecutando procedimiento…", () -> {

                // Invocamos el método del repositorio
                // encargado de ejecutar cambiar_ciudad.
                objetos.cambiarCiudad(c.id(), ciudad);

                // Recuperamos nuevamente los clientes
                // para visualizar la dirección modificada.
                return new Cambio<>(
                        "Ciudad cambiada mediante el procedimiento.",
                        objetos.listar(null)
                );

            }, this::mostrarCambioObjetos);

        } catch (IllegalArgumentException e) {

            aviso(e.getMessage());
        }
    }


    // =====================================================
    // MODELO OBJETO-RELACIONAL: ACTUALIZAR JSONB
    // =====================================================

    /**
     * Sustituye el documento JSON de preferencias
     * del cliente seleccionado.
     */
    @FXML
    private void cambiarJson() {

        Cliente c = seleccionado();

        if (c == null) {
            return;
        }

        try {

            // Recuperamos el contenido del área de texto.
            //
            // obligatorio() comprueba que exista contenido.
            // Esto no implica necesariamente que el JSON
            // tenga una sintaxis válida.
            String json = Validacion.obligatorio(
                    txtOJson.getText(),
                    "Preferencias JSON"
            );

            ejecutar("Actualizando JSONB…", () -> {

                // Delegamos en el repositorio la actualización
                // de la columna preferencias de PostgreSQL.
                boolean ok = objetos.cambiarPreferencias(
                        c.id(),
                        json
                );

                return new Cambio<>(
                        ok
                                ? "Preferencias sustituidas."
                                : "El cliente ya no existe.",
                        objetos.listar(null)
                );

            }, this::mostrarCambioObjetos);

        } catch (IllegalArgumentException e) {

            aviso(e.getMessage());
        }
    }


    // =====================================================
    // MODELO OBJETO-RELACIONAL: ELIMINAR
    // =====================================================

    /**
     * Elimina el cliente objeto-relacional seleccionado.
     */
    @FXML
    private void eliminarObjetos() {

        Cliente c = seleccionado();

        if (c == null) {
            return;
        }

        // Solicitamos confirmación al usuario.
        if (!confirmar(
                "Eliminar a " + c.nombre() + " de objeto-relacional"
        )) {
            return;
        }

        ejecutar("Eliminando cliente…", () -> {

            // El DAO realiza la eliminación mediante el ID.
            boolean ok = objetos.eliminar(c.id());

            return new Cambio<>(
                    ok
                            ? "Cliente eliminado."
                            : "El cliente ya no existe.",
                    objetos.listar(null)
            );

        }, this::mostrarCambioObjetos);
    }


    // =====================================================
    // FUNCIÓN ALMACENADA: TOTAL DE CLIENTES
    // =====================================================

    /**
     * Consulta el número total de clientes mediante
     * la función almacenada total_clientes de PostgreSQL.
     */
    @FXML
    private void totalObjetos() {

        ejecutar(
                "Consultando función total_clientes…",

                // Referencia a un método.
                // Equivale a: () -> objetos.total()
                objetos::total,

                // Recibimos el resultado de la función
                // y actualizamos las etiquetas de JavaFX.
                total -> {

                    lblTotal.setText(
                            "Total en BD: " + total
                    );

                    lblEstado.setText(
                            "Función ejecutada correctamente."
                    );
                }
        );
    }


    // =====================================================
    // ACTUALIZACIÓN DE LA INTERFAZ OBJETO-RELACIONAL
    // =====================================================

    /**
     * Actualiza la tabla y el formulario después de
     * realizar una modificación en la base de datos.
     */
    private void mostrarCambioObjetos(Cambio<Cliente> cambio) {

        // Sustituimos el contenido de la tabla.
        tablaObjetos.getItems().setAll(cambio.filas());

        // Eliminamos los filtros anteriores.
        txtOFiltro.clear();

        // Limpiamos el formulario.
        limpiarObjetos();

        // Invalidamos el total mostrado anteriormente,
        // ya que una inserción o eliminación podría
        // haber modificado el número de clientes.
        lblTotal.setText(
                "Total en BD: pulsa Consultar total"
        );

        lblEstado.setText(cambio.mensaje());
    }


    /**
     * Limpia todos los campos del formulario
     * objeto-relacional.
     */
    @FXML
    private void limpiarObjetos() {

        tablaObjetos.getSelectionModel().clearSelection();

        // Limpiamos los atributos simples.
        txtONombre.clear();
        txtOEmail.clear();

        // Limpiamos los atributos de la dirección.
        txtOCalle.clear();
        txtOCiudad.clear();
        txtOCp.clear();

        // Limpiamos los teléfonos.
        txtOTelefonos.clear();

        // Establecemos un documento JSON inicial.
        //
        // Las barras invertidas permiten introducir
        // comillas dobles dentro de una cadena Java.
        txtOJson.setText("{\"canal\":\"email\"}");
    }


    /**
     * Recupera el cliente seleccionado en la tabla
     * objeto-relacional.
     *
     * Centralizamos esta comprobación para evitar
     * repetirla en todos los métodos.
     *
     * @return Cliente seleccionado o null.
     */
    private Cliente seleccionado() {

        Cliente c =
                tablaObjetos.getSelectionModel().getSelectedItem();

        if (c == null) {

            aviso(
                    "Selecciona un cliente de la tabla objeto-relacional."
            );
        }

        return c;
    }


    // =====================================================
    // VENTANAS DE DIÁLOGO
    // =====================================================

    /**
     * Muestra una ventana de confirmación.
     *
     * @param texto Mensaje mostrado al usuario.
     * @return true si el usuario pulsa Aceptar.
     */
    private boolean confirmar(String texto) {

        // Creamos una alerta de tipo CONFIRMATION.
        Alert a = alerta(
                Alert.AlertType.CONFIRMATION,
                "Confirmar eliminación",
                texto + "?"
        );

        // showAndWait() muestra la ventana y espera
        // a que el usuario seleccione una opción.
        //
        // Optional permite representar la posibilidad
        // de que no se haya obtenido ningún resultado.
        Optional<ButtonType> r = a.showAndWait();

        // Solo devolvemos true si existe un resultado
        // y el botón seleccionado es OK.
        return r.isPresent() && r.get() == ButtonType.OK;
    }


    /**
     * Muestra un mensaje informativo al usuario.
     */
    private void aviso(String texto) {

        alerta(
                Alert.AlertType.INFORMATION,
                "Revisa los datos",
                texto
        ).showAndWait();
    }


    // =====================================================
    // GESTIÓN DE ERRORES
    // =====================================================

    /**
     * Centraliza el tratamiento de las excepciones.
     *
     * Si el error procede de JDBC, utilizamos SQLState
     * para identificar su naturaleza y mostrar un mensaje
     * más comprensible para el usuario.
     *
     * @param error Excepción producida.
     */
    private void error(Throwable error) {

        // Recuperamos el mensaje de la excepción.
        // Si no existe, utilizamos el nombre de su clase.
        String detalle = error.getMessage() == null
                ? error.getClass().getSimpleName()
                : error.getMessage();

        // Comprobamos si se trata de una SQLException.
        //
        // Pattern matching permite declarar directamente
        // la variable e con el tipo SQLException.
        if (error instanceof SQLException e) {

            // SQLState es un código de cinco caracteres
            // que identifica la categoría del error SQL.

            detalle = switch (
                    e.getSQLState() == null
                            ? ""
                            : e.getSQLState()
                    ) {

                // Funcionalidad no soportada.
                case "0A000" -> e.getMessage();

                // Incumplimiento de una restricción UNIQUE.
                case "23505" ->
                        "Ya existe un cliente con ese email.";

                // Incumplimiento de una restricción CHECK.
                case "23514" ->
                        "Se incumple una restricción. "
                                + "Revisa dirección, CP y canal del JSON.";

                // Representación textual no válida
                // para el tipo de dato correspondiente.
                case "22P02" ->
                        "El JSON o algún dato enviado "
                                + "no tiene un formato válido.";

                // Código no_data_found de PostgreSQL.
                case "P0002" ->
                        "El cliente seleccionado ya no existe.";

                // Tabla inexistente o esquema no válido.
                case "42P01", "3F000" ->
                        "Falta el esquema o la tabla. "
                                + "Ejecuta los scripts de montaje indicados en la guía.";

                // Función o procedimiento no encontrado.
                case "42883" ->
                        "No existe la función o el procedimiento. "
                                + "Completa el script de rutinas.";

                // Tratamiento de cualquier otro error SQL.
                default ->
                        "Error SQL [" + e.getSQLState()
                                + "]: " + detalle;
            };
        }

        // Actualizamos la etiqueta de estado.
        lblEstado.setText(
                "No se pudo completar la operación."
        );

        // Mostramos una ventana con los detalles del error.
        alerta(
                Alert.AlertType.ERROR,
                "Operación pendiente o error",
                detalle
        ).showAndWait();
    }


    /**
     * Método auxiliar para construir ventanas de diálogo.
     *
     * Evita repetir la configuración de las alertas.
     *
     * @param tipo Tipo de alerta.
     * @param titulo Título de la ventana.
     * @param texto Contenido del mensaje.
     * @return Alerta configurada.
     */
    private Alert alerta(
            Alert.AlertType tipo,
            String titulo,
            String texto
    ) {

        Alert a = new Alert(tipo);

        // Configuramos el título y el contenido.
        a.setTitle(titulo);

        // No utilizamos una cabecera adicional.
        a.setHeaderText(null);

        a.setContentText(texto);

        // Si el contenedor está asociado a una escena,
        // establecemos su ventana como propietaria
        // de la alerta.
        if (zonaTrabajo.getScene() != null) {

            a.initOwner(
                    zonaTrabajo.getScene().getWindow()
            );
        }

        // Permitimos modificar el tamaño de la alerta.
        a.setResizable(true);

        return a;
    }


    // =====================================================
    // CIERRE DE LA APLICACIÓN
    // =====================================================

    /**
     * Libera los recursos asociados al controlador
     * cuando se cierra la aplicación.
     */
    public void cerrar() {

        // Impedimos que se inicien nuevas operaciones
        // o se procesen resultados pendientes.
        cerrado = true;

        // Solicitamos la interrupción de las tareas activas
        // y evitamos que se ejecuten las tareas pendientes.
        executor.shutdownNow();
    }


    // =====================================================
    // RECORD AUXILIAR
    // =====================================================

    /**
     * Record genérico que permite agrupar dos resultados:
     *
     * - Un mensaje sobre la operación realizada.
     * - Una lista actualizada de registros.
     *
     * T puede representar tanto ClienteBasico como Cliente.
     *
     * Java genera automáticamente el constructor y
     * los métodos de acceso mensaje() y filas().
     */
    private record Cambio<T>(
            String mensaje,
            List<T> filas
    ) {
    }
}