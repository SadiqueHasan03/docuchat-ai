import { useEffect, useRef } from 'react';
import * as THREE from 'three';

export default function ThreeScene() {
  const canvasRef = useRef(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return undefined;

    const scene = new THREE.Scene();
    const camera = new THREE.PerspectiveCamera(42, 1, 0.1, 100);
    camera.position.z = 8;

    const renderer = new THREE.WebGLRenderer({
      canvas,
      alpha: true,
      antialias: false,
      powerPreference: 'low-power',
    });
    renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.5));

    const group = new THREE.Group();
    scene.add(group);

    const pointCount = 72;
    const points = [];
    for (let i = 0; i < pointCount; i += 1) {
      const angle = i * 2.399963;
      const radius = 1.35 + (i % 7) * 0.16;
      points.push(new THREE.Vector3(
        Math.cos(angle) * radius,
        Math.sin(angle) * radius,
        ((i % 9) - 4) * 0.12,
      ));
    }

    const positions = new Float32Array(pointCount * 3);
    points.forEach((point, index) => point.toArray(positions, index * 3));
    const pointGeometry = new THREE.BufferGeometry();
    pointGeometry.setAttribute('position', new THREE.BufferAttribute(positions, 3));

    const pointMaterial = new THREE.PointsMaterial({
      color: '#b3a5ff',
      size: 0.045,
      transparent: true,
      opacity: 0.62,
      sizeAttenuation: true,
    });
    group.add(new THREE.Points(pointGeometry, pointMaterial));

    const linePositions = [];
    points.forEach((point, index) => {
      const neighbor = points[(index + 7) % pointCount];
      linePositions.push(...point.toArray(), ...neighbor.toArray());
    });
    const lineGeometry = new THREE.BufferGeometry();
    lineGeometry.setAttribute('position', new THREE.Float32BufferAttribute(linePositions, 3));
    const lineMaterial = new THREE.LineBasicMaterial({
      color: '#7868cf',
      transparent: true,
      opacity: 0.16,
    });
    group.add(new THREE.LineSegments(lineGeometry, lineMaterial));

    const ambientLight = new THREE.AmbientLight('#a596ff', 1.5);
    scene.add(ambientLight);

    const resize = () => {
      const { width, height } = canvas.getBoundingClientRect();
      if (!width || !height) return;
      renderer.setSize(width, height, false);
      camera.aspect = width / height;
      camera.updateProjectionMatrix();
    };

    const observer = new ResizeObserver(resize);
    observer.observe(canvas);
    resize();

    const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    let frameId = 0;
    const render = (time = 0) => {
      group.rotation.y = prefersReducedMotion ? -0.18 : Math.sin(time * 0.00012) * 0.16 - 0.18;
      group.rotation.x = prefersReducedMotion ? 0.08 : Math.cos(time * 0.0001) * 0.05 + 0.08;
      renderer.render(scene, camera);
      if (!prefersReducedMotion) frameId = window.requestAnimationFrame(render);
    };
    render();

    return () => {
      window.cancelAnimationFrame(frameId);
      observer.disconnect();
      pointGeometry.dispose();
      pointMaterial.dispose();
      lineGeometry.dispose();
      lineMaterial.dispose();
      renderer.dispose();
    };
  }, []);

  return <canvas aria-hidden="true" className="three-scene" ref={canvasRef} />;
}
