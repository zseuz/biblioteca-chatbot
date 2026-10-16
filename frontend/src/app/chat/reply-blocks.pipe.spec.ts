import { toBlocks } from './reply-blocks.pipe';

/** Cómo se trocea una respuesta en párrafos y listas. */
describe('toBlocks', () => {
  it('separa la cabecera de la lista', () => {
    expect(toBlocks('Hay 2 préstamos vencidos:\n- «Rayuela» – Ana\n- «Dune» – Luis')).toEqual([
      { kind: 'text', text: 'Hay 2 préstamos vencidos:' },
      { kind: 'list', items: ['«Rayuela» – Ana', '«Dune» – Luis'] },
    ]);
  });

  it('una línea en blanco separa párrafos; las líneas seguidas forman uno', () => {
    expect(toBlocks('¡Hola!\n\nPuedo ayudarte.\nPregunta lo que quieras.')).toEqual([
      { kind: 'text', text: '¡Hola!' },
      { kind: 'text', text: 'Puedo ayudarte.\nPregunta lo que quieras.' },
    ]);
  });

  it('el texto tras una lista empieza un párrafo nuevo', () => {
    expect(toBlocks('Ana tiene:\n- «Dune»\nEn total 7.')).toEqual([
      { kind: 'text', text: 'Ana tiene:' },
      { kind: 'list', items: ['«Dune»'] },
      { kind: 'text', text: 'En total 7.' },
    ]);
  });

  it('un guion sin espacio (p. ej. un número negativo) no es una lista', () => {
    expect(toBlocks('-5 grados')).toEqual([{ kind: 'text', text: '-5 grados' }]);
  });

  it('respuesta vacía', () => {
    expect(toBlocks('')).toEqual([]);
  });
});
